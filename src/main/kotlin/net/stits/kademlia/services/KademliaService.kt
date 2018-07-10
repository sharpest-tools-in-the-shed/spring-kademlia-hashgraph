package net.stits.kademlia.services

import kotlinx.coroutines.experimental.runBlocking
import net.stits.kademlia.controllers.KademliaMessageTypes
import net.stits.kademlia.controllers.TOPIC_KADEMLIA_COMMON
import net.stits.kademlia.data.*
import net.stits.osen.Address
import net.stits.osen.Message
import net.stits.osen.P2P
import net.stits.osen.SerializationUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigInteger
import javax.annotation.PostConstruct


@Service
class KademliaService {
    @Autowired
    lateinit var discoveryService: DiscoveryService

    @Autowired
    lateinit var identityService: IdentityService

    @Autowired
    lateinit var p2p: P2P

    private val timeout = 10000L

    @PostConstruct
    fun init() {
        p2p.setBeforePackageSent { pack ->
            val additionalMetadata = KademliaAdditionalMetadata.create(pack) { identityService.getKeyPair() }
            pack.metadata.additionalMetadata = SerializationUtils.anyToBytes(additionalMetadata)
        }

        p2p.setAfterPackageReceived { pack ->
            require(KademliaAdditionalMetadata.verify(pack)) { "Package verification failed" }
        }
    }

    fun bootstrap(bootstrapAddress: Address, myId: BigInteger) {
        runBlocking {
            println("Trying to bootstrap $myId with node $bootstrapAddress")

            p2p.requestFromTimeouted<FindNodeResponse>(bootstrapAddress, timeout) {
                val request = FindNodeRequest(myId, myId, BigInteger.ZERO)
                Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_NODE, request)
            } ?: throw RuntimeException("Unable to bootstrap $myId with node $bootstrapAddress")

            findNode(myId)
        }
    }

    fun ping(id: BigInteger): String {
        assertBootstrapped()

        var nodeAddr = discoveryService.getNodeById(id)
        if (nodeAddr == null) nodeAddr = findNode(id)
        if (nodeAddr == null) throw RuntimeException("Unable to find node: $id")

        if (sendPing(nodeAddr)) return "Ok"
        throw RuntimeException("Unable to reach node: $nodeAddr")
    }

    fun store(id: BigInteger, value: Any): Boolean {
        assertBootstrapped()

        val nodeToStore = findNode(id)
                ?: throw RuntimeException("Cannot find closest node to id: $id. Maybe your address book is empty?")

        return sendStore(id, value, nodeToStore).success
    }

    fun findNode(id: BigInteger): KAddress? {
        assertBootstrapped()

        val nodeFromAddressBook = discoveryService.getNodeById(id)

        if (nodeFromAddressBook != null) return nodeFromAddressBook

        val nodesToAsk = discoveryService.getClosestKBucket(id)
        if (nodesToAsk.isEmpty()) return null

        return nodesToAsk.map { askForNodeRecursive(id, it) }.find { it != null }
                ?: discoveryService.getClosest(id)
    }

    fun findValue(id: BigInteger): Any? {
        assertBootstrapped()

        val nodesToAsk = discoveryService.getClosestKBucket(id)
        if (nodesToAsk.isEmpty()) return null

        return nodesToAsk.map { askForValueRecursive(id, it) }.find { it != null }
    }

    private fun assertBootstrapped() {
        assert(
                discoveryService.addressBook != null
                        && discoveryService.addressBook!!.getFlat().isNotEmpty()
        ) { "Unable to do any actions before bootstrap." }
    }

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
        p2p.requestFromTimeouted(to.getAddress(), timeout) {
            val payload = DefaultPayload(identityService.getId(), to.getId())
            Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.PING, payload)
        } ?: false
    }

    private fun sendFindNode(id: BigInteger, to: KAddress) = runBlocking {
        p2p.requestFromTimeouted<FindNodeResponse>(to.getAddress(), timeout) {
            val payload = FindNodeRequest(id, identityService.getId(), to.getId())
            Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_NODE, payload)

        } ?: throw RuntimeException("Unable to receive closest nodes for id: $id from node $to")
    }

    private fun sendFindValue(id: BigInteger, to: KAddress) = runBlocking {
        p2p.requestFromTimeouted<FindValueResponse>(to.getAddress(), timeout) {
            val payload = FindValueRequest(id, identityService.getId(), to.getId())
            Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_VALUE, payload)
        } ?: throw RuntimeException("Unable to get value of id: $id from node: $to")
    }

    private fun sendStore(id: BigInteger, value: Any, to: KAddress) = runBlocking {
        p2p.requestFromTimeouted<StoreResponse>(to.getAddress(), timeout) {
            val payload = StoreRequest(id, value, identityService.getId(), to.getId())
            Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.STORE, payload)
        } ?: throw RuntimeException("Unable to store value of id: $id on node: $to")
    }
}
