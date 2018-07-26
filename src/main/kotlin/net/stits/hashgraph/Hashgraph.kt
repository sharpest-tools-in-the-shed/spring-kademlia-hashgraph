package net.stits.hashgraph

import java.math.BigInteger

typealias EventId = BigInteger
typealias CreatorId = BigInteger
typealias Round = BigInteger
typealias Signature = BigInteger

/**
 * Future improvements:
 * todo 0. let it be generator
 * todo 1. remove old events starting from some round (e.g. currentRound - 10) = store only slice of hashgraph
 * todo 2. allow new issuers to participate
 * todo 3. allow first events without parents of issuers to participate
 * todo 4. profile
 */
class Hashgraph : Iterable<HashgraphEvent> {
    internal val events = hashMapOf<Round, HashgraphEvent>()
    internal val orderedEvents = mutableListOf<EventId>()
    internal val eventsWithoutParents = mutableListOf<HashgraphEvent>()
    internal val lastEventByParticipants = hashMapOf<CreatorId, EventId>()

    internal val eventsByRound = hashMapOf<Round, MutableList<EventId>>()
    internal val famousWitnessesByRound = hashMapOf<Round, MutableList<EventId>>()
    internal val unFamousWitnessesByRound = hashMapOf<Round, MutableList<EventId>>()

    internal val firstEventsWhichKnowAboutEvent = hashMapOf<EventId, MutableList<EventId>>()
    // mapping [EventId -> CreatorId which voted YES]
    internal val elections = hashMapOf<EventId, MutableList<CreatorId>>()

    fun processEvent(event: HashgraphEvent) {
        val eventId = event.id()

        validateEvent(event)
        eventsWithoutParents.remove(event)

        if (event.isGenesis()) {
            addEvent(event, Round.ONE)
            addWitness(eventId, Round.ONE)

            eventsWithoutParents.forEach { processEvent(it) }
            return
        }

        val (round, isWitness) = calculateRoundAndDecideWitness(event)

        detectFirstEventsWhichKnowAboutEvent(event) // really bad name =C

        if (!isWitness) {
            addEvent(event, round)
            eventsWithoutParents.forEach { processEvent(it) }

            return
        }

        vote(event, round)
        val newFamousWitnesses = processElections(event)

        addEvent(event, round)
        addWitness(event, round)

        if (newFamousWitnesses.isEmpty()) {
            eventsWithoutParents.forEach { processEvent(it) }
            return
        }

        val newOrderedEvents = applyOrder(newFamousWitnesses)
        orderedEvents.addAll(newOrderedEvents)

        eventsWithoutParents.forEach { processEvent(it) }
    }

    override fun iterator(): Iterator<HashgraphEvent> {
        return orderedEvents.map { events[it]!! }.iterator()
    }

    /**
     * Returns ordered events
     *
     * TODO: apply only on transactions
     */
    internal fun applyOrder(famousWitnesses: List<EventId>): List<EventId> {
        val ancestorsList = famousWitnesses.map { events[it]!! }.map { getAncestors(it) }

        // only those events which are ancestors of all famous witnesses at a time
        val ancestors = ancestorsList
                .fold(ancestorsList.first()) { acc, list -> acc.intersect(list).toList() }

        val unorderedAncestors = ancestors.filter { !orderedEvents.contains(it) }.distinct()

        val orderedEvents = unorderedAncestors.map { event ->
            val roundReceived = getRoundOfEvent(famousWitnesses.first())
            val sortedTimestamps = firstEventsWhichKnowAboutEvent[event]!!
                    .map { events[it]!! }
                    .filter { firstDescendant -> famousWitnesses.map { events[it]!! }.all { canSee(it, firstDescendant) } }
                    .map { it.timestamp }
                    .sorted()

            val consensusTimestamp = sortedTimestamps[sortedTimestamps.size / 2]

            val whitenedSignature = whitenedSignature(event, famousWitnesses)

            OrderedEvent(roundReceived, consensusTimestamp, whitenedSignature, event)
        }

        return orderedEvents
                .sortedWith(compareBy(OrderedEvent::roundReceived, OrderedEvent::consensusTimestamp, OrderedEvent::whitenedSignature))
                .map { it.id }
    }

    data class OrderedEvent(val roundReceived: Round, val consensusTimestamp: Long, val whitenedSignature: Signature, val id: EventId)

    internal fun whitenedSignature(id: EventId, witnessesIds: List<EventId>): Signature {
        val event = events[id]!!
        val witnesses = witnessesIds.map { events[it]!! }
        val signatures = mutableListOf(event.signature)
        signatures.addAll(witnesses.map { it.signature })

        return signatures.fold(Signature.ZERO) { acc, signature -> acc.xor(signature) }
    }

    /**
     * Returns witnesses that are now famous
     */
    internal fun processElections(event: HashgraphEvent): List<EventId> {
        val electionsWhichAreAboutToEnd = elections.entries.filter { it.value.size >= getSupermajorityOf(lastEventByParticipants.keys) }

        val roundsAndWitnesses = electionsWhichAreAboutToEnd
                .filter { canStronglySee(event, events[it.key]!!) }
                .map {
                    val round = makeWitnessFamous(it.key)
                    elections.remove(it.key)
                    Pair(round, it.key)
                }

        // picking only new witnesses which have largest round
        val maxRound = roundsAndWitnesses.maxBy { it.first }?.first ?: return emptyList()

        return roundsAndWitnesses
                .filter { it.first == maxRound }
                .map { it.second }
    }

    internal fun vote(event: HashgraphEvent, round: Round) {
        val previousRoundsWitnesses = getUnFamousWitnesses(BigInteger.ONE, round - BigInteger.ONE)
        previousRoundsWitnesses.map { events[it]!! }.forEach { if (canSee(event, it)) addVote(it.id(), event) }
    }

    internal fun addVote(to: EventId, from: HashgraphEvent) {
        val voteIssuer = from.creatorId()
        if (elections[to] == null)
            elections[to] = mutableListOf(voteIssuer)
        else if (!elections[to]!!.contains(voteIssuer))
            elections[to]!!.add(voteIssuer)
    }

    internal fun calculateRoundAndDecideWitness(event: HashgraphEvent): Pair<Round, Boolean> {
        val selfParentRound = getRoundOfEvent(event.selfParentId!!)
        val otherParentRound = getRoundOfEvent(event.otherParentId!!)

        val round = if (selfParentRound > otherParentRound) selfParentRound else otherParentRound
        val witnessesOfRound = getAllWitnesses(round)
        val nextRound = witnessesOfRound
                .map { events[it]!! }
                .map { canStronglySee(event, it) }
                .count { it } >= getSupermajorityOf(witnessesOfRound)

        val resultRound = if (nextRound) round + BigInteger.ONE else round
        val resultWitness = resultRound > selfParentRound

        return Pair(resultRound, resultWitness)
    }

    internal fun canSee(who: HashgraphEvent, whom: HashgraphEvent) = who.id() == whom.id() || getAncestors(who).contains(whom.id())
    internal fun canStronglySee(who: HashgraphEvent, whom: HashgraphEvent): Boolean {
        if (!canSee(who, whom)) return false

        val eventsToCheck = getAncestors(who).map { events[it]!! }

        return eventsToCheck
                .filter { canSee(it, whom) }
                .map { it.creatorId() }
                .distinct()
                .count() >= getSupermajorityOf(lastEventByParticipants.keys)
    }

    internal fun detectFirstEventsWhichKnowAboutEvent(event: HashgraphEvent) {
        val eventId = event.id()
        firstEventsWhichKnowAboutEvent[eventId] = mutableListOf(eventId)

        getAncestorsThatHaveLessThanSupermajorityOfFirstDescendants(event)
                .map { events[it]!! }
                .forEach { ancestor ->
                    val descendants = firstEventsWhichKnowAboutEvent[ancestor.id()]
                    if (descendants == null) {
                        firstEventsWhichKnowAboutEvent[ancestor.id()] = mutableListOf(eventId)
                        return@forEach
                    }

                    if (descendants.map { events[it]!! }
                                    .any {
                                        it.creatorId() == event.creatorId()
                                    }
                    ) return@forEach

                    firstEventsWhichKnowAboutEvent[ancestor.id()]!!.add(eventId)
                }
    }

    internal fun getAncestorsThatHaveLessThanSupermajorityOfFirstDescendants(event: HashgraphEvent): MutableList<EventId> {
        val supermajority = getSupermajorityOf(lastEventByParticipants.keys)

        return getAncestors(event)
                .filter { firstEventsWhichKnowAboutEvent[it] == null || firstEventsWhichKnowAboutEvent[it]!!.size < supermajority }
                .toMutableList()
    }

    internal fun addEvent(event: HashgraphEvent, round: Round) {
        val id = event.id()
        events[id] = event
        lastEventByParticipants[event.creatorId()] = id

        if (getEvents(round).isEmpty())
            eventsByRound[round] = mutableListOf(id)
        else
            eventsByRound[round]!!.add(id)
    }

    internal fun validateEvent(event: HashgraphEvent) {
        val id = event.id()
        require(!events.containsKey(id)) { "Hashgraph already contains event $event" }

        if (event.isGenesis()) {
            require(!lastEventByParticipants.containsKey(event.creatorId())) {
                "Hashgraph already contains genesis event from ${event.creatorId()}"
            }
        } else {
            require(parentsPresent(event)) {
                if (!eventsWithoutParents.contains(event)) eventsWithoutParents.add(event)
                "Hashgraph doesn't contain parents for event $event"
            }

            require(forksNotPresent(event)) { "Unable to add event $event (possible fork creation attempt)" }
        }
    }

    internal fun getRoundOfEvent(id: EventId) = eventsByRound.entries.find { it.value.contains(id) }!!.key

    /**
     * Actually this event doesn't check for forks. But it is absolutely equal to check if events by the same creator are
     * added sequentially. Sequence prevents forks.
     */
    private fun forksNotPresent(event: HashgraphEvent) = lastEventByParticipants[event.creatorId()] == event.selfParentId

    private fun parentsPresent(event: HashgraphEvent): Boolean {
        return events.containsKey(event.selfParentId) && events.containsKey(event.otherParentId)
    }

    // cached recursion so it shouldn't eat much stack and should work fast
    private val ancestors = hashMapOf<EventId, List<EventId>>()
    internal fun getAncestors(event: HashgraphEvent): List<EventId> {
        val id = event.id()
        val fromCache = ancestors[id]
        if (fromCache != null) return fromCache

        val result = getAncestorsRec(event)
        ancestors[id] = result

        return result
    }
    private fun getAncestorsRec(event: HashgraphEvent): List<EventId> {
        if (event.isGenesis()) return emptyList()

        val selfParent = events[event.selfParentId!!]!!
        val otherParent = events[event.otherParentId!!]!!

        val result = mutableListOf(event.selfParentId, event.otherParentId)

        val selfParentAncestors = getAncestors(selfParent)
        result.addAll(selfParentAncestors)

        val otherParentAncestors = getAncestors(otherParent)
        result.addAll(otherParentAncestors)

        return result.distinct()
    }

    private val selfAncestors = hashMapOf<EventId, List<EventId>>()
    internal fun getSelfAncestors(event: HashgraphEvent): List<EventId> {
        val id = event.id()
        val fromCache = selfAncestors[id]
        if (fromCache != null) return fromCache

        val result = getSelfAncestorsRec(event)
        selfAncestors[id] = result

        return result
    }
    private fun getSelfAncestorsRec(event: HashgraphEvent): List<EventId> {
        if (event.isGenesis()) return emptyList()

        val selfParent = events[event.selfParentId!!]!!
        val result = mutableListOf(event.selfParentId)

        val selfParentSelfAncestors = getSelfAncestors(selfParent)
        result.addAll(selfParentSelfAncestors)

        return result.distinct()
    }

    internal fun getEvents(): List<HashgraphEvent> {
        return events.values.toList()
    }
    internal fun getEvents(of: Round): List<HashgraphEvent> {
        return eventsByRound[of]?.map { events[it]!! } ?: emptyList()
    }
    internal fun getEvents(from: Round, to: Round): List<HashgraphEvent> {
        return eventsByRound.filterKeys { it in (from..to) }.values.flatten().map { events[it]!! }
    }

    internal fun addWitness(event: HashgraphEvent, round: Round) = addWitness(event.id(), round)
    internal fun addWitness(id: EventId, round: Round) {
        require(!getAllWitnesses().contains(id)) { "Hashgraph already contains witness id: $id" }

        if (unFamousWitnessesByRound[round] == null)
            unFamousWitnessesByRound[round] = mutableListOf()

        unFamousWitnessesByRound[round]!!.add(id)
    }
    internal fun makeWitnessFamous(event: HashgraphEvent) = makeWitnessFamous(event.id())
    internal fun makeWitnessFamous(id: EventId): Round {
        require(getUnFamousWitnesses().contains(id)) { "Hashgraph doesn't contains unfamous witness id: $id" }

        val round = unFamousWitnessesByRound.entries.first { it.value.contains(id) }.key
        unFamousWitnessesByRound[round]!!.remove(id)

        if (famousWitnessesByRound[round] == null)
            famousWitnessesByRound[round] = mutableListOf()

        famousWitnessesByRound[round]!!.add(id)
        return round
    }
    internal fun getAllWitnesses(): List<EventId> {
        val result = getFamousWitnesses().toMutableList()
        result.addAll(getUnFamousWitnesses())

        return result
    }

    internal fun getAllWitnesses(of: Round): List<EventId> {
        val result = getFamousWitnesses(of).toMutableList()
        result.addAll(getUnFamousWitnesses(of))

        return result
    }

    internal fun getAllWitnesses(from: Round, to: Round): List<EventId> {
        val result = getFamousWitnesses(from, to).toMutableList()
        result.addAll(getUnFamousWitnesses(from, to))

        return result
    }

    internal fun getFamousWitnesses(): List<EventId> {
        if (famousWitnessesByRound.isEmpty()) return emptyList()

        return famousWitnessesByRound.values.flatten()
    }

    internal fun getFamousWitnesses(of: Round): List<EventId> {
        return famousWitnessesByRound[of] ?: return emptyList()
    }

    internal fun getFamousWitnesses(from: Round, to: Round): List<EventId> {
        return famousWitnessesByRound.filterKeys { it in (from..to) }.values.flatten()
    }

    internal fun getUnFamousWitnesses(): List<EventId> {
        if (unFamousWitnessesByRound.isEmpty()) return emptyList()

        return unFamousWitnessesByRound.values.flatten()
    }

    internal fun getUnFamousWitnesses(of: Round): List<EventId> {
        return unFamousWitnessesByRound[of] ?: return emptyList()
    }

    internal fun getUnFamousWitnesses(from: Round, to: Round): List<EventId> {
        return unFamousWitnessesByRound.filterKeys { it in (from..to) }.values.flatten()
    }
}

fun <T> getSupermajorityOf(collection: Collection<T>): Int = if (collection.size % 2 == 0 || collection.size == 1) collection.size * 2 / 3 + 1 else collection.size * 2 / 3