package net.stits.kademlia.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.stits.MY_ADDRESS
import net.stits.kademlia.controllers.KademliaMessageTypes
import net.stits.kademlia.controllers.TOPIC_KADEMLIA_COMMON
import net.stits.kademlia.data.*
import net.stits.osen.Address
import net.stits.osen.Message
import net.stits.osen.P2P
import org.springframework.stereotype.Service
import java.math.BigInteger


typealias IdGenerator = (value: Any) -> BigInteger

@Service
class KademliaService(private val discoveryService: DiscoveryService) {
    private val mapper = ObjectMapper().registerModule(KotlinModule())
    private var bootstrapped = false

    fun bootstrap(bootstrapAddress: Address, myId: BigInteger) {
        println("Trying to bootstrap $myId with node $bootstrapAddress")

        val request = DefaultPayload(myId, BigInteger.ZERO)
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_NODE_REQ, request)

        P2P.send(bootstrapAddress, message, MY_ADDRESS.getPort(), _class = FindNodeResponse::class.java)
                ?: throw RuntimeException("Unable to bootstrap $myId with node $bootstrapAddress")

        bootstrapped = true
        println("Bootstrap success. Saying about me to others...")

        findNode(myId)

        println("Others are notified")
    }

    fun ping(id: BigInteger): Boolean {
        assertBootstrapped()

        var nodeAddr = discoveryService.getNodeById(id)
        if (nodeAddr == null) nodeAddr = findNode(id)
        if (nodeAddr == null) return false

        return sendPing(nodeAddr)
    }

    fun store(value: Any, generateId: IdGenerator): Boolean {
        assertBootstrapped()

        val id = generateId(value)
        val serializedValue = mapper.writeValueAsBytes(value)

        val nodeToStore = findNode(id)
                ?: throw RuntimeException("Cannot find closest node to id: $id. Maybe your address book is empty?")

        return sendStore(id, serializedValue, nodeToStore).success
    }

    fun findNode(id: BigInteger): KAddress? {
        assertBootstrapped()

        val nodeFromAddressBook = discoveryService.getNodeById(id)

        if (nodeFromAddressBook != null) return nodeFromAddressBook

        val nodesToAsk = discoveryService.getClosestKBucket(id)
        if (nodesToAsk.isEmpty()) return null

        return nodesToAsk.map { askForNodeRecursive(id, it) }.find { it != null } ?: discoveryService.getClosest(id)
    }

    fun <T> findValue(id: BigInteger, _class: Class<T>): T? {
        assertBootstrapped()

        val nodesToAsk = discoveryService.getClosestKBucket(id)
        if (nodesToAsk.isEmpty()) return null

        val value = nodesToAsk.map { askForValueRecursive(id, it) }.find { it != null }

        return if (value == null) null
        else mapper.readValue(value, _class)
    }

    private fun assertBootstrapped() {
        assert(bootstrapped) { "Unable to do any actions before bootstrap." }
    }

    private fun askForNodeRecursive(id: BigInteger, node: KAddress): KAddress? {
        val response = sendFindNode(id, node)
        if (response.nodesToAsk.isEmpty()) return null
        if (discoveryService.containsAll(response.nodesToAsk)) return null

        val notAskedNodes = discoveryService.addNewNodes(response.nodesToAsk)

        return response.nodesToAsk.find { it.getId() == id }
                ?: notAskedNodes.map { askForNodeRecursive(id, it) }.find { it != null }
    }

    private fun askForValueRecursive(id: BigInteger, node: KAddress): ByteArray? {
        val response = sendFindValue(id, node)
        if (response.value != null) return response.value
        if (response.nodesToAsk == null || response.nodesToAsk.isEmpty()) return null

        val notAskedNodes = discoveryService.addNewNodes(response.nodesToAsk)

        return notAskedNodes.map { askForValueRecursive(id, it) }.find { it != null }
    }

    private fun sendPing(to: KAddress): Boolean {
        val payload = DefaultPayload(MY_ADDRESS.getId(), to.getId())
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.PING, payload)

        val result = P2P.send(to.getAddress(), message, MY_ADDRESS.getPort(), _class = Boolean::class.java)
        return result != null
    }

    private fun sendFindNode(id: BigInteger, to: KAddress): FindNodeResponse {
        val payload = FindNodeRequest(id, MY_ADDRESS.getId(), to.getId())
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_NODE_REQ, payload)

        val result = P2P.send(to.getAddress(), message, MY_ADDRESS.getPort(), _class = FindNodeResponse::class.java)
                ?: throw RuntimeException("Unable to receive closest nodes for id: $id from node $to")

        return result as FindNodeResponse
    }

    private fun sendFindValue(id: BigInteger, to: KAddress): FindValueResponse {
        val payload = FindValueRequest(id, MY_ADDRESS.getId(), to.getId())
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_VALUE_REQ, payload)

        val result = P2P.send(to.getAddress(), message, MY_ADDRESS.getPort(), _class = FindValueResponse::class.java)
                ?: throw RuntimeException("Unable to get value of id: $id from node: $to")

        return result as FindValueResponse
    }

    private fun sendStore(id: BigInteger, value: ByteArray, to: KAddress): StoreResponse {
        val payload = StoreRequest(id, value, MY_ADDRESS.getId(), to.getId())
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.STORE_REQ, payload)

        val result = P2P.send(to.getAddress(), message, MY_ADDRESS.getPort(), _class = StoreResponse::class.java)
                ?: throw RuntimeException("Unable to store value of id: $id on node: $to")

        return result as StoreResponse
    }
}