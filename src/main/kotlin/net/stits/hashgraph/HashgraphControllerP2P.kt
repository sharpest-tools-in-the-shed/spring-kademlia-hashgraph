package net.stits.hashgraph

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import net.stits.hashgraph.services.ConsensusService
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
    @Autowired
    lateinit var consensusService: ConsensusService

    @Autowired
    lateinit var identityService: IdentityService

    private val eventBuilder = HashgraphEventBuilder()

    @PostConstruct
    fun createGenesis() {
        val genesisEvent = eventBuilder.buildGenesis(identityService.getKeyPair())
        consensusService.addEvent(genesisEvent)
    }

    @On(HashgraphMessageTypes.SYNC)
    fun handleSync(message: HashgraphSyncMessage, sender: Address) {
        println("Got events ${message.events} from $sender")

        if (message.events.isEmpty()) return
        consensusService.addEvents(message.events)

        val myId = identityService.getId()
        val selfParentId = consensusService.getLastEventBy(myId)
        val otherParentId = consensusService.getLastEventBy(message.senderId)

        val newEvent = eventBuilder.withSelfParent(selfParentId).withOtherParent(otherParentId).build(identityService.getKeyPair())
        consensusService.addEvent(newEvent)

        runBlocking {
            delay(100)
            consensusService.syncToSomeone()
        }
    }
}