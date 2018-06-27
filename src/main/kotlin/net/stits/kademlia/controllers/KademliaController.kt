package net.stits.kademlia.controllers

import net.stits.MY_ADDRESS
import net.stits.kademlia.data.*
import net.stits.kademlia.services.DiscoveryService
import net.stits.kademlia.services.StorageService
import net.stits.osen.*


const val TOPIC_KADEMLIA_COMMON = "KAD_COMMON"
object KademliaMessageTypes {
    const val PING = "PING"
    const val PONG = "PONG"
    const val STORE_REQ = "STORE_REQ"
    const val STORE_RES = "STORE_RES"
    const val FIND_NODE_REQ = "FIND_NODE_REQ"
    const val FIND_NODE_RES = "FIND_NODE_RES"
    const val FIND_VALUE_REQ = "FIND_VALUE_REQ"
    const val FIND_VALUE_RES = "FIND_VALUE_RES"
}


@P2PController(TOPIC_KADEMLIA_COMMON)
class KademliaController(private val discoveryService: DiscoveryService, private val storageService: StorageService) {

    @On(KademliaMessageTypes.PING)
    fun handlePing(sender: Address, request: DefaultPayload) {
        println("Got PING message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        val response = DefaultPayload(from = MY_ADDRESS.getId(), to = request.from)
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.PONG, response)

        P2P.send(sender, message, MY_ADDRESS.getPort())
    }

    @On(KademliaMessageTypes.PONG)
    fun handlePong(sender: Address, request: DefaultPayload) {
        println("Got PONG message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)
    }

    @On(KademliaMessageTypes.FIND_NODE_REQ)
    fun handleFindNodeRequest(sender: Address, request: FindNodeRequest) {
        println("Got FIND_NODE_REQ message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        val nodesToAsk = discoveryService.getClosestKBucket(request.id)
        val response = FindNodeResponse(id = request.id, nodesToAsk = nodesToAsk, from = MY_ADDRESS.getId(), to = request.from)
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_NODE_RES, response)

        P2P.send(sender, message, MY_ADDRESS.getPort())
    }

    @On(KademliaMessageTypes.FIND_NODE_RES)
    fun handleFindNodeResponse(sender: Address, request: FindNodeResponse) {
        println("Got FIND_NODE_RES message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        // TODO: find needed node and handle it or send FIND_NODE_REQ to all these guys
    }

    @On(KademliaMessageTypes.FIND_VALUE_REQ)
    fun handleFindValueRequest(sender: Address, request: FindValueRequest) {
        println("Got FIND_NODE_RES message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        val value = storageService.get(request.id)
        val nodesToAsk = discoveryService.getClosestKBucket(request.id)

        val response: FindValueResponse = if (value != null)
            FindValueResponse(id = request.id, value = value, from = MY_ADDRESS.getId(), to = request.from)
        else
            FindValueResponse(id = request.id, nodesToAsk = nodesToAsk, from = MY_ADDRESS.getId(), to = request.from)

        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_VALUE_RES, response)

        P2P.send(sender, message, MY_ADDRESS.getPort())
    }

    @On(KademliaMessageTypes.FIND_VALUE_RES)
    fun handleFindValueResponse(sender: Address, request: FindValueResponse) {
        println("Got FIND_NODE_RES message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        // TODO: handle found value or send FIND_VALUE_REQ to all these guys
    }

    @On(KademliaMessageTypes.STORE_REQ)
    fun handleStoreRequest(sender: Address, request: StoreRequest) {
        println("Got FIND_NODE_RES message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        val success = storageService.put(request.id, request.value)
        val response = StoreResponse(id = request.id, success = success, from = MY_ADDRESS.getId(), to = request.from)
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.STORE_RES, response)

        P2P.send(sender, message, MY_ADDRESS.getPort())
    }
}