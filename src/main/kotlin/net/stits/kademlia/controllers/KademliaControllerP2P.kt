package net.stits.kademlia.controllers


import net.stits.kademlia.data.*
import net.stits.kademlia.services.DiscoveryService
import net.stits.kademlia.services.IdentityService
import net.stits.kademlia.services.StorageService
import net.stits.osen.Address
import net.stits.osen.On
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

    @On(KademliaMessageTypes.PING)
    fun handlePing(sender: Address, request: DefaultPayload): DefaultPayload {
        println("Got PING message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        return DefaultPayload(from = identityService.getId(), to = request.from)
    }

    @On(KademliaMessageTypes.FIND_NODE)
    fun handleFindNodeRequest(sender: Address, request: FindNodeRequest): FindNodeResponse {
        println("Got FIND_NODE_REQ message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        val nodesToAsk = discoveryService.getClosestKBucket(request.id)
        return FindNodeResponse(id = request.id, nodesToAsk = nodesToAsk, from = identityService.getId(), to = request.from)
    }

    @On(KademliaMessageTypes.FIND_VALUE)
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

    @On(KademliaMessageTypes.STORE)
    fun handleStoreRequest(sender: Address, request: StoreRequest): StoreResponse {
        println("Got STORE_REQ message from: $sender, payload: $request")

        discoveryService.addNode(KAddress(sender, request.from)) // TODO [relays]: this should be something else (idc now)

        storageService.put(request.id, request.value)
        return StoreResponse(id = request.id, success = true, from = identityService.getId(), to = request.from)
    }
}
