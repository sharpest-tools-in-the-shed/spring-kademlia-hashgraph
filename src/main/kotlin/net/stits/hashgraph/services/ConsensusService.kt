package net.stits.hashgraph.services

import kotlinx.coroutines.experimental.runBlocking
import net.stits.hashgraph.*
import net.stits.kademlia.data.KAddress
import net.stits.kademlia.services.DiscoveryService
import net.stits.kademlia.services.IdentityService
import net.stits.osen.Message
import net.stits.osen.P2P
import net.stits.utils.randomOrNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


data class EventInfo(
    val id: EventId,
    val selfParent: EventId?,
    val otherParent: EventId?,
    val consensusReached: Boolean
) {
    constructor(event: HashgraphEvent, consensusReached: Boolean) : this(
        event.id(),
        event.selfParentId,
        event.otherParentId,
        consensusReached
    )
}

@Service
class ConsensusService {
    val hg = Hashgraph()

    fun addEvent(event: HashgraphEvent) {
        hg.processEvent(event)
    }

    fun addEvents(vararg events: HashgraphEvent) {
        events.forEach { hg.processEvent(it) }
    }

    fun addEvents(events: Collection<HashgraphEvent>) {
        events.forEach { hg.processEvent(it) }
    }

    data class OrderedLastEvent(val eventId: EventId, val round: Round, val index: Int)

    fun getLastEventBy(id: CreatorId) = hg.getLastEventsByParticipants()[id]
    fun getLastEvents() = hg.getLastEventsByParticipants()
    fun getLastEventsSortedByAddingOrder(): List<EventId> = hg.getEventsInAddOrder().reversed()

    fun getEvents(): List<HashgraphEvent> {
        return hg.getEventsInAddOrder().map { hg.getEventById(it)!! }
    }

    fun getEvents(from: EventId): List<HashgraphEvent> {
        val events = getEvents()

        val fromEvent = events.find { it.id() == from } ?: return emptyList()
        val fromIndex = events.indexOf(fromEvent)

        return events.subList(fromIndex, events.lastIndex)
    }

    fun getEventsInfo(): List<EventInfo> {
        val allEvents = hg.getEvents()
        val consensusEvents = hg.getConsensusEvents().map { hg.getEventById(it)!! }
        val unorderedEvents = allEvents.minus(consensusEvents)

        val result = mutableListOf<EventInfo>()
        consensusEvents.forEach { result.add(EventInfo(it, true)) }
        unorderedEvents.forEach { result.add(EventInfo(it, false)) }

        return result
    }

    fun getEventsInfo(from: EventId): List<EventInfo> {
        val events = getEventsInfo()

        val fromEvent = events.find { it.id == from } ?: return emptyList()
        val fromIndex = events.indexOf(fromEvent)

        return events.filterIndexed { index, _ -> index > fromIndex }
    }

    // TODO: to networking service all this mess
    private val lastEventSentToPeer = hashMapOf<CreatorId, EventId>()

    @Autowired
    lateinit var p2p: P2P

    @Autowired
    lateinit var discoveryService: DiscoveryService

    @Autowired
    lateinit var identityService: IdentityService

    fun syncWithPeer(peer: KAddress) {
        val lastEvent = lastEventSentToPeer[peer.getId()]

        val events = if (lastEvent == null) getEvents() else getEvents(lastEvent)

        println("New events: $events")

        sendEvents(peer, events)

        lastEventSentToPeer[peer.getId()] = events.last().id()

        println("Sent events $events to $peer")
    }

    fun sendEvents(peer: KAddress, events: List<HashgraphEvent>) = runBlocking {
        val payload = HashgraphSyncMessage(events, identityService.getId())
        val message = Message(TOPIC_HASHGRAPH, HashgraphMessageTypes.SYNC, payload)

        p2p.send(peer.getAddress(), message)
    }

    fun syncWithRandom() {
        val peer = discoveryService.toList().randomOrNull()

        if (peer != null)
            syncWithPeer(peer)

    }

    fun startSyncing() = syncWithRandom()
}