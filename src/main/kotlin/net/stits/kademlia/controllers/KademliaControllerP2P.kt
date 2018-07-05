package net.stits.kademlia.controllers


import net.stits.kademlia.data.*
import net.stits.kademlia.services.DiscoveryService
import net.stits.kademlia.services.IdentityService
import net.stits.kademlia.services.StorageService
import net.stits.osen.*
import org.springframework.beans.factory.annotation.Autowired


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
class KademliaControllerP2P {
    @Autowired
    lateinit var p2p: P2P

    @Autowired
    lateinit var discoveryService: DiscoveryService

    @Autowired
    lateinit var storageService: StorageService

    @Autowired
    lateinit var identityService: IdentityService


    @On(KademliaMessageTypes.PING)
    fun handlePing(sender: Address, request: DefaultPayload, session: Session) {
        println("Got PING message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        val response = DefaultPayload(from = identityService.getId(), to = request.from)
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.PONG, response)

        p2p.send(sender, message, identityService.getPort(), _session = session)
    }

    @On(KademliaMessageTypes.PONG)
    fun handlePong(sender: Address, response: DefaultPayload): String {
        println("Got PONG message from: $sender, payload: $response")

        discoveryService.addNode(KAddress(sender, response.from)) // TODO [relays]: this should be something else (idc now)

        return "true"
    }

    @On(KademliaMessageTypes.FIND_NODE_REQ)
    fun handleFindNodeRequest(sender: Address, request: FindNodeRequest, session: Session) {
        println("Got FIND_NODE_REQ message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        val nodesToAsk = discoveryService.getClosestKBucket(request.id)
        val response = FindNodeResponse(id = request.id, nodesToAsk = nodesToAsk, from = identityService.getId(), to = request.from)
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_NODE_RES, response)

        p2p.send(sender, message, identityService.getPort(), _session = session)
    }

    @On(KademliaMessageTypes.FIND_NODE_RES)
    fun handleFindNodeResponse(sender: Address, response: FindNodeResponse): FindNodeResponse {
        println("Got FIND_NODE_RES message from: $sender, payload: $response")

        discoveryService.addNode(KAddress(sender, response.from)) // TODO [relays]: this should be something else (idc now)

        return response
    }

    @On(KademliaMessageTypes.FIND_VALUE_REQ)
    fun handleFindValueRequest(sender: Address, request: FindValueRequest, session: Session) {
        println("Got FIND_VALUE_REQ message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        val value = storageService.get(request.id)
        val nodesToAsk = discoveryService.getClosestKBucket(request.id)

        val response: FindValueResponse = if (value != null)
            FindValueResponse(id = request.id, value = value, from = identityService.getId(), to = request.from)
        else
            FindValueResponse(id = request.id, nodesToAsk = nodesToAsk, from = identityService.getId(), to = request.from)

        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.FIND_VALUE_RES, response)

        p2p.send(sender, message, identityService.getPort(), _session = session)
    }

    @On(KademliaMessageTypes.FIND_VALUE_RES)
    fun handleFindValueResponse(sender: Address, response: FindValueResponse): FindValueResponse {
        println("Got FIND_VALUE_RES message from: $sender, payload: $response")

        discoveryService.addNode(KAddress(sender, response.from)) // TODO [relays]: this should be something else (idc now)

        return response
    }

    @On(KademliaMessageTypes.STORE_REQ)
    fun handleStoreRequest(sender: Address, request: StoreRequest, session: Session) {
        println("Got STORE_REQ message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        val success = storageService.put(request.id, request.value)
        val response = StoreResponse(id = request.id, success = success, from = identityService.getId(), to = request.from)
        val message = Message(TOPIC_KADEMLIA_COMMON, KademliaMessageTypes.STORE_RES, response)

        p2p.send(sender, message, identityService.getPort(), _session = session)
    }

    @On(KademliaMessageTypes.STORE_RES)
    fun handleStoreResponse(sender: Address, response: StoreResponse): StoreResponse {
        println("Got STORE_RES message from: $sender, payload: $response")

        discoveryService.addNode(KAddress(sender, response.from)) // TODO [relays]: this should be something else (idc now)

        return response
    }
}
