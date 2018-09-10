package net.stits.hashgraph

import net.stits.osen.loggerFor
import java.math.BigInteger

typealias EventId = BigInteger
typealias CreatorId = BigInteger
typealias Round = BigInteger
typealias Signature = BigInteger

/**
 * Future improvements:
 * todo 0. clean
 * todo 1. remove old events starting from some round (e.g. currentRound - 10) = store only slice of hashgraph (very important)
 * todo 2. allow new issuers to participate
 * todo 3. allow first events_without_parents of issuers to participate
 * todo 4. profile
 */
class Hashgraph {
    /**
     * Contains events that were added to hashgraph successfully
     */
    private val events = hashMapOf<EventId, HashgraphEvent>()
    /**
     * Contains event ids in order that these events were added
     */
    private val eventsInAddOrder = mutableListOf<EventId>()
    /**
     * Contains events that are now famous in consensus order (Round Received > Consensus Timestamp > Whitened Signature)
     */
    private val consensusEvents = mutableListOf<EventId>()
    /**
     * Contains events which can't be added because of missing parents.
     * These guys are added eventually, when their parents appear
     */
    private val eventsWithoutParents = mutableListOf<HashgraphEvent>()
    /**
     * Contains last event by every creator
     */
    private val lastEventByParticipants = hashMapOf<CreatorId, EventId>()
    /**
     * Contains events grouped by round
     */
    private val eventsByRound = hashMapOf<Round, MutableList<EventId>>()
    /**
     * Contains famous witnesses grouped by round
     */
    private val famousWitnessesByRound = hashMapOf<Round, MutableList<EventId>>()
    /**
     * Contains non-famous witnesses grouped by round
     */
    private val unFamousWitnessesByRound = hashMapOf<Round, MutableList<EventId>>()
    /**
     * Contains mapping [EventId -> List of first events which see that event unique by creator]
     */
    private val firstEventsWhichKnowAboutEvent = hashMapOf<EventId, MutableList<EventId>>()
    /**
     * Contains mapping [EventId -> List of creators which think this event is famous]
     */
    private val elections = hashMapOf<EventId, MutableList<CreatorId>>()

    fun getEventsInAddOrder() = eventsInAddOrder
    fun getLastEventsByParticipants() = lastEventByParticipants
    fun getEventsWithoutParents() = eventsWithoutParents
    fun getConsensusEvents() = consensusEvents
    fun getEventById(id: EventId): HashgraphEvent? = events[id]
    fun getRoundOfEvent(id: EventId) = eventsByRound.entries.find { it.value.contains(id) }?.key
    fun getEvents() = events.values.toMutableList()
    fun getEvents(of: Round) = eventsByRound[of]?.map { events[it]!! } ?: emptyList()
    fun getEvents(from: Round, to: Round) = eventsByRound
            .filterKeys { it in (from..to) }.values
            .flatten()
            .map { events[it]!! }

    companion object {
        val logger = loggerFor<Hashgraph>()
    }

    fun processEvent(event: HashgraphEvent) {
        val success = process(event)

        if (success) {
            while (processEventsWithoutParents()) {}
        }
    }

    /**
     * Main method that handles all the dirty job
     * It validates event, applies algorithm to hashgraph using this new event and adds it
     */
    fun process(event: HashgraphEvent): Boolean {
        if (!isValid(event)) return false

        val eventId = event.id()

        if (event.isGenesis()) {
            addEvent(event, Round.ONE)
            addWitness(eventId, Round.ONE)

            return true
        }

        val (round, isWitness) = calculateRoundAndDecideWitness(event)

        detectFirstEventsWhichKnowAboutEvent(event) // really bad name =C

        if (!isWitness) {
            addEvent(event, round)

            return true
        }

        vote(event, round)
        val newFamousWitnesses = processElections(event)

        addEvent(event, round)
        addWitness(event, round)

        if (newFamousWitnesses.isEmpty()) return true

        val newOrderedEvents = applyOrder(newFamousWitnesses)
        consensusEvents.addAll(newOrderedEvents)

        return true
    }

    fun processEventsWithoutParents(): Boolean {
        val eventsWithoutParentsToDelete = mutableListOf<HashgraphEvent>()

        val success = eventsWithoutParents.map {
            val success = process(it)
            if (success) eventsWithoutParentsToDelete.add(it)

            success
        }

        eventsWithoutParentsToDelete.forEach { eventsWithoutParents.remove(it) }

        return success.any { it }
    }

    /**
     * Returns ordered consensus events
     *
     * TODO: apply only on transactions
     */
    internal fun applyOrder(famousWitnesses: List<EventId>): List<EventId> {
        val ancestorsList = famousWitnesses.map { events[it]!! }.map { getAncestors(it) }

        // only those events which are ancestors of all famous witnesses at a time
        val ancestors = ancestorsList
                .fold(ancestorsList.first()) { acc, list -> acc.intersect(list).toList() }

        val unorderedAncestors = ancestors.filter { !getConsensusEvents().contains(it) }.distinct()

        val orderedEvents = unorderedAncestors.map { event ->
            val roundReceived = getRoundOfEvent(famousWitnesses.first())!!
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

    /**
     * Some internal class for complex sorting processing
     */
    private data class OrderedEvent(val roundReceived: Round, val consensusTimestamp: Long, val whitenedSignature: Signature, val id: EventId)

    /**
     * Calculates whitened signature - xor of all events signatures
     */
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

    /**
     * Votes for every event which it can vote
     */
    internal fun vote(event: HashgraphEvent, round: Round) {
        val previousRoundsWitnesses = getUnFamousWitnesses(BigInteger.ONE, round - BigInteger.ONE)
        previousRoundsWitnesses.map { events[it]!! }.forEach { if (canSee(event, it)) addVote(it.id(), event) }
    }

    /**
     * Physically adds vote to elections collection
     */
    internal fun addVote(to: EventId, from: HashgraphEvent) {
        val voteIssuer = from.creatorId
        if (elections[to] == null)
            elections[to] = mutableListOf(voteIssuer)
        else if (!elections[to]!!.contains(voteIssuer))
            elections[to]!!.add(voteIssuer)
    }

    /**
     * So, as name says, it calculates round relative to parents round and marks event as witness if it is
     */
    internal fun calculateRoundAndDecideWitness(event: HashgraphEvent): Pair<Round, Boolean> {
        val selfParentRound = getRoundOfEvent(event.selfParentId!!)
        val otherParentRound = getRoundOfEvent(event.otherParentId!!)

        val round = if (selfParentRound!! > otherParentRound!!) selfParentRound else otherParentRound
        val witnessesOfRound = getAllWitnesses(round)
        val nextRound = witnessesOfRound
                .map { events[it]!! }
                .map { canStronglySee(event, it) }
                .count { it } >= getSupermajorityOf(witnessesOfRound)

        val resultRound = if (nextRound) round + BigInteger.ONE else round
        val resultWitness = resultRound > selfParentRound

        return Pair(resultRound, resultWitness)
    }

    /**
     * If creator knows about event
     *
     * @param who - event by creator
     * @param whom - event that creator knows about
     */
    internal fun canSee(who: HashgraphEvent, whom: HashgraphEvent) = who.id() == whom.id() || getAncestors(who).contains(whom.id())

    /**
     * If creator knows that 2/3 of other creators are also know about event
     *
     * @param who - event by creator
     * @param whom - event that creators know about
     */
    internal fun canStronglySee(who: HashgraphEvent, whom: HashgraphEvent): Boolean {
        if (!canSee(who, whom)) return false

        val eventsToCheck = getAncestors(who).map { events[it]!! }

        return eventsToCheck
                .filter { canSee(it, whom) }
                .map { it.creatorId }
                .distinct()
                .count() >= getSupermajorityOf(lastEventByParticipants.keys)
    }

    /**
     * TODO: very bad name
     *
     * So it finds all events (TODO: possible optimization - there should be only one such event)
     * for which this event is first descendant unique by creator
     */
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

                    val sameCreator = descendants
                            .map { events[it]!! }
                            .any { it.creatorId == event.creatorId }

                    if (sameCreator) return@forEach

                    firstEventsWhichKnowAboutEvent[ancestor.id()]!!.add(eventId)
                }
    }

    /**
     * TODO: very bad name
     *
     * Returns ancestors of event which have less than 2n/3 events which creators know about that event
     */
    internal fun getAncestorsThatHaveLessThanSupermajorityOfFirstDescendants(event: HashgraphEvent): MutableList<EventId> {
        val supermajority = getSupermajorityOf(lastEventByParticipants.keys)

        return getAncestors(event)
                .filter { firstEventsWhichKnowAboutEvent[it] == null || firstEventsWhichKnowAboutEvent[it]!!.size < supermajority }
                .toMutableList()
    }

    /**
     * Adds event to hashgraph assuming it valid
     */
    internal fun addEvent(event: HashgraphEvent, round: Round) {
        val id = event.id()
        events[id] = event

        lastEventByParticipants[event.creatorId] = id
        eventsInAddOrder.add(id)

        if (getEvents(round).isEmpty())
            eventsByRound[round] = mutableListOf(id)
        else
            eventsByRound[round]!!.add(id)
    }

    /**
     * Validates event
     * Prevents: replications, event forks and missing parents
     */
    internal fun isValid(event: HashgraphEvent): Boolean {
        val id = event.id()
        if (events.containsKey(id)) {
            logger.info("Hashgraph already contains event $event")
            return false
        }

        if (event.isGenesis()) {
            if (lastEventByParticipants.containsKey(event.creatorId)) {
                logger.info("Hashgraph already contains genesis event from ${event.creatorId}")
                return false
            }
        } else {
            if (!parentsPresent(event)) {
                if (!eventsWithoutParents.contains(event)) eventsWithoutParents.add(event)

                logger.info("Hashgraph doesn't contain parents for event $event")
                return false
            }

            if (!forksNotPresent(event)) {
                logger.info("Unable to add event $event (possible fork creation attempt)")
                return false
            }
        }

        return true
    }

    /**
     * Actually this event doesn't check for forks. But it is absolutely equal to check if events by the same creator are
     * added sequentially. Sequence prevents forks.
     */
    private fun forksNotPresent(event: HashgraphEvent) = lastEventByParticipants[event.creatorId] == event.selfParentId

    /**
     * Checks if event's parents are already in hashgraph
     */
    private fun parentsPresent(event: HashgraphEvent): Boolean {
        return events.containsKey(event.selfParentId) && events.containsKey(event.otherParentId)
    }

    /**
     * Gets ancestors of event
     * All 'ancestors', 'getAncestors' and 'getAncestorsRec' are needed to perform this action fast
     * Using cached recursion so it shouldn't eat much stack and should work fast
     */
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

    /**
     * Gets self ancestors of event
     * All 'selfAncestors', 'getSelfAncestors' and 'getSelfAncestorsRec' are needed to perform this action fast
     * Using cached recursion so it shouldn't eat much stack and should work fast
     */
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

    /**
     * Marks event as non-famous witness
     */
    internal fun addWitness(event: HashgraphEvent, round: Round) = addWitness(event.id(), round)
    internal fun addWitness(id: EventId, round: Round) {
        require(!getAllWitnesses().contains(id)) { "Hashgraph already contains witness id: $id" }

        if (unFamousWitnessesByRound[round] == null)
            unFamousWitnessesByRound[round] = mutableListOf()

        unFamousWitnessesByRound[round]!!.add(id)
    }

    /**
     * Marks non-famous witness as famous witness
     */
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

    /**
     * Returns all witnesses
     */
    internal fun getAllWitnesses(): List<EventId> {
        val result = getFamousWitnesses().toMutableList()
        result.addAll(getUnFamousWitnesses())

        return result
    }

    /**
     * Returns witnesses by round
     */
    internal fun getAllWitnesses(of: Round): List<EventId> {
        val result = getFamousWitnesses(of).toMutableList()
        result.addAll(getUnFamousWitnesses(of))

        return result
    }

    /**
     * Returns witnesses by range of rounds (from..to)
     */
    internal fun getAllWitnesses(from: Round, to: Round): List<EventId> {
        val result = getFamousWitnesses(from, to).toMutableList()
        result.addAll(getUnFamousWitnesses(from, to))

        return result
    }

    /**
     * Returns all famous witnesses
     */
    internal fun getFamousWitnesses(): List<EventId> {
        if (famousWitnessesByRound.isEmpty()) return emptyList()

        return famousWitnessesByRound.values.flatten()
    }

    /**
     * Returns famous witnesses by round
     */
    internal fun getFamousWitnesses(of: Round): List<EventId> {
        return famousWitnessesByRound[of] ?: return emptyList()
    }

    /**
     * Returns famous witnesses by range of rounds (from..to)
     */
    internal fun getFamousWitnesses(from: Round, to: Round): List<EventId> {
        return famousWitnessesByRound.filterKeys { it in (from..to) }.values.flatten()
    }

    /**
     * Returns all non-famous witnesses
     */
    internal fun getUnFamousWitnesses(): List<EventId> {
        if (unFamousWitnessesByRound.isEmpty()) return emptyList()

        return unFamousWitnessesByRound.values.flatten()
    }

    /**
     * Returns non-famous witnesses by round
     */
    internal fun getUnFamousWitnesses(of: Round): List<EventId> {
        return unFamousWitnessesByRound[of] ?: return emptyList()
    }

    /**
     * Returns non-famous witnesses by range of rounds (from..to)
     */
    internal fun getUnFamousWitnesses(from: Round, to: Round): List<EventId> {
        return unFamousWitnessesByRound.filterKeys { it in (from..to) }.values.flatten()
    }
}

/**
 * Returns 2/3 of collection size
 */
fun <T> getSupermajorityOf(collection: Collection<T>): Int = if (collection.size % 2 == 0 || collection.size == 1) collection.size * 2 / 3 + 1 else collection.size * 2 / 3