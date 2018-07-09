package net.stits.kademlia.controllers


import net.stits.kademlia.data.*
import net.stits.kademlia.services.DiscoveryService
import net.stits.kademlia.services.IdentityService
import net.stits.kademlia.services.StorageService
import net.stits.osen.Address
import net.stits.osen.OnRequest
import net.stits.osen.OnResponse
import net.stits.osen.P2PController
import org.springframework.beans.factory.annotation.Autowired


const val TOPIC_KADEMLIA_COMMON = "KAD_COMMON"
object KademliaMessageTypes {
    const val PING = "PING"
    const val STORE = "STORE"
    const val FIND_NODE = "FIND_NODE"
    const val FIND_VALUE = "FIND_VALUE"
}


@P2PController(TOPIC_KADEMLIA_COMMON)
class KademliaControllerP2P {
    @Autowired
    lateinit var discoveryService: DiscoveryService

    @Autowired
    lateinit var storageService: StorageService

    @Autowired
    lateinit var identityService: IdentityService


    @OnRequest(KademliaMessageTypes.PING)
    fun handlePing(sender: Address, request: DefaultPayload): DefaultPayload {
        println("Got PING message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        return DefaultPayload(from = identityService.getId(), to = request.from)
    }

    @OnResponse(KademliaMessageTypes.PING)
    fun handlePong(sender: Address, response: DefaultPayload): Boolean {
        println("Got PONG message from: $sender, payload: $response")

        discoveryService.addNode(KAddress(sender, response.from)) // TODO [relays]: this should be something else (idc now)

        return true
    }

    @OnRequest(KademliaMessageTypes.FIND_NODE)
    fun handleFindNodeRequest(sender: Address, request: FindNodeRequest): FindNodeResponse {
        println("Got FIND_NODE_REQ message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        val nodesToAsk = discoveryService.getClosestKBucket(request.id)
        return FindNodeResponse(id = request.id, nodesToAsk = nodesToAsk, from = identityService.getId(), to = request.from)
    }

    @OnResponse(KademliaMessageTypes.FIND_NODE)
    fun handleFindNodeResponse(sender: Address, response: FindNodeResponse): FindNodeResponse {
        println("Got FIND_NODE_RES message from: $sender, payload: $response")

        discoveryService.addNode(KAddress(sender, response.from)) // TODO [relays]: this should be something else (idc now)

        return response
    }

    @OnRequest(KademliaMessageTypes.FIND_VALUE)
    fun handleFindValueRequest(sender: Address, request: FindValueRequest): FindValueResponse {
        println("Got FIND_VALUE_REQ message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        val value = storageService.get(request.id)
        val nodesToAsk = discoveryService.getClosestKBucket(request.id)

        return if (value != null)
            FindValueResponse(id = request.id, value = value, from = identityService.getId(), to = request.from)
        else
            FindValueResponse(id = request.id, nodesToAsk = nodesToAsk, from = identityService.getId(), to = request.from)
    }

    @OnResponse(KademliaMessageTypes.FIND_VALUE)
    fun handleFindValueResponse(sender: Address, response: FindValueResponse): FindValueResponse {
        println("Got FIND_VALUE_RES message from: $sender, payload: $response")

        discoveryService.addNode(KAddress(sender, response.from)) // TODO [relays]: this should be something else (idc now)

        return response
    }

    @OnRequest(KademliaMessageTypes.STORE)
    fun handleStoreRequest(sender: Address, request: StoreRequest): StoreResponse {
        println("Got STORE_REQ message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        val success = storageService.put(request.id, request.value)
        return StoreResponse(id = request.id, success = success, from = identityService.getId(), to = request.from)
    }

    @OnResponse(KademliaMessageTypes.STORE)
    fun handleStoreResponse(sender: Address, response: StoreResponse): StoreResponse {
        println("Got STORE_RES message from: $sender, payload: $response")

        discoveryService.addNode(KAddress(sender, response.from)) // TODO [relays]: this should be something else (idc now)

        return response
    }
}
