package net.stits.kademlia.services

import kotlinx.coroutines.experimental.runBlocking
import net.stits.kademlia.controllers.KademliaMessageTypes
import net.stits.kademlia.controllers.TOPIC_KADEMLIA_COMMON
import net.stits.kademlia.data.*
import net.stits.osen.Address
import net.stits.osen.Message
import net.stits.osen.P2P
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigInteger


@Service
class KademliaService {
    @Autowired
    lateinit var discoveryService: DiscoveryService

    @Autowired
    lateinit var identityService: IdentityService

    @Autowired
    lateinit var p2p: P2P

    fun bootstrap(bootstrapAddress: Address, myId: BigInteger) {
        runBlocking {
            println("Trying to bootstrap $myId with node $bootstrapAddress")

            val request = FindNodeRequest(myId, myId, BigInteger.ZERO)
            val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_NODE, request)

            val response = p2p.sendAndReceive<FindNodeResponse>(bootstrapAddress, message)
            discoveryService.addNodes(response.nodesToAsk)

            findNode(myId)
        }
    }

    fun ping(id: BigInteger): String {
        if (!isBootstrapped()) throw RuntimeException("Unable to reach node: $id")

        val nodeAddr: KAddress = discoveryService.getNodeById(id)
            ?: findNode(id)
            ?: throw RuntimeException("Unable to find node: $id")

        if (sendPing(nodeAddr)) return "Ok"
        throw RuntimeException("Unable to reach node: $nodeAddr")
    }

    fun store(id: BigInteger, value: Any): Boolean {
        if (!isBootstrapped()) {
            throw RuntimeException("Not bootstrapped!")
        }

        val nodeToStore = findNode(id)
            ?: throw RuntimeException("Cannot find closest node to id: $id. Maybe your address book is empty?")

        return sendStore(id, value, nodeToStore).success
    }

    fun findNode(id: BigInteger): KAddress? {
        if (!isBootstrapped()) {
            throw RuntimeException("Not bootstrapped!")
        }

        val nodeFromAddressBook = discoveryService.getNodeById(id)
        if (nodeFromAddressBook != null) return nodeFromAddressBook

        val nodesToAsk = discoveryService.getClosestKBucket(id)
        if (nodesToAsk.isEmpty()) return null

        return nodesToAsk
            .map { askForNodeRecursive(id, it) }
            .find { it != null }
            ?: discoveryService.getClosest(id)
    }

    fun findValue(id: BigInteger): Any? {
        if (!isBootstrapped()) {
            throw RuntimeException("Not bootstrapped!")
        }

        val nodesToAsk = discoveryService.getClosestKBucket(id)
        if (nodesToAsk.isEmpty()) return null

        return nodesToAsk.map { askForValueRecursive(id, it) }.find { it != null }
    }

    private fun isBootstrapped() = discoveryService.addressBook.getFlat().isNotEmpty()

    private fun askForNodeRecursive(id: BigInteger, node: KAddress): KAddress? {
        val response = sendFindNode(id, node)
        if (response.nodesToAsk.isEmpty()) return null
        if (discoveryService.containsAll(response.nodesToAsk)) return null

        val notAskedNodes = discoveryService.addNewNodes(response.nodesToAsk)

        return response.nodesToAsk.find { it.getId() == id }
            ?: notAskedNodes.map { askForNodeRecursive(id, it) }.find { it != null }
    }

    private fun askForValueRecursive(id: BigInteger, node: KAddress): Any? {
        val response = sendFindValue(id, node)
        if (response.value != null) return response.value
        if (response.nodesToAsk == null || response.nodesToAsk.isEmpty()) return null

        val notAskedNodes = discoveryService.addNewNodes(response.nodesToAsk)

        return notAskedNodes.map { askForValueRecursive(id, it) }.find { it != null }
    }

    private fun sendPing(to: KAddress): Boolean = runBlocking {
        val payload = DefaultPayload(identityService.getId(), to.getId())
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.PING, payload)

        try {
            p2p.sendAndReceive<DefaultPayload>(to.getAddress(), message)
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    private fun sendFindNode(id: BigInteger, to: KAddress) = runBlocking {
        val payload = FindNodeRequest(id, identityService.getId(), to.getId())
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_NODE, payload)

        p2p.sendAndReceive<FindNodeResponse>(to.getAddress(), message)
    }

    private fun sendFindValue(id: BigInteger, to: KAddress) = runBlocking {
        val payload = FindValueRequest(id, identityService.getId(), to.getId())
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_VALUE, payload)

        p2p.sendAndReceive<FindValueResponse>(to.getAddress(), message)
    }

    private fun sendStore(id: BigInteger, value: Any, to: KAddress) = runBlocking {
        val payload = StoreRequest(id, value, identityService.getId(), to.getId())
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.STORE, payload)

        p2p.sendAndReceive<StoreResponse>(to.getAddress(), message)
    }
}
