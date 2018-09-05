package net.stits.hashgraph

import net.stits.hashgraph.services.ConsensusService
import net.stits.kademlia.data.KAddress
import net.stits.kademlia.services.DiscoveryService
import net.stits.kademlia.services.IdentityService
import net.stits.osen.Address
import net.stits.osen.On
import net.stits.osen.P2PController
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigInteger
import javax.annotation.PostConstruct


const val TOPIC_HASHGRAPH = "__HG"

object HashgraphMessageTypes {
    const val SYNC = "SYNC"
}

data class HashgraphSyncMessage(val events: List<HashgraphEvent>, val senderId: CreatorId)
typealias State = BigInteger

@P2PController(TOPIC_HASHGRAPH)
class HashgraphControllerP2P {
    private lateinit var lastSyncedPeer: KAddress

    @Autowired
    lateinit var consensusService: ConsensusService

    @Autowired
    lateinit var identityService: IdentityService

    @Autowired
    lateinit var discoveryService: DiscoveryService

    private val eventBuilder = HashgraphEventBuilder()

    @PostConstruct
    fun createGenesis() {
        val genesisEvent = eventBuilder.buildGenesis(identityService.getKeyPair())
        consensusService.addEvent(genesisEvent)

        lastSyncedPeer = identityService.getKAddress()
    }

    @On(HashgraphMessageTypes.SYNC)
    fun handleSyncReq(message: HashgraphSyncMessage, sender: Address) {
        println("Got events ${message.events} from $sender")

        discoveryService.addNode(KAddress(sender, message.senderId))
        consensusService.addEvents(message.events)

        val myId = identityService.getId()
        val selfParentId = consensusService.getLastEventBy(myId)
        val otherParentId = consensusService.getLastEventBy(message.senderId)

        val newEvent = eventBuilder
            .withSelfParent(selfParentId)
            .withOtherParent(otherParentId)
            .build(identityService.getKeyPair())

        consensusService.addEvent(newEvent)
        consensusService.syncWithRandom()
    }
}