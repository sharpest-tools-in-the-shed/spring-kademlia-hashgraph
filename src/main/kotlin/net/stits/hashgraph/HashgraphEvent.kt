package net.stits.hashgraph

import net.stits.osen.SerializationUtils
import net.stits.osen.loggerFor
import net.stits.utils.CryptoUtils
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey
import java.util.*


// TODO: implement secure and flexible transactions (maybe use antlr)
data class HashgraphTransaction(
        val key: String,
        val value: Any?
)

class HashgraphEventBuilder {
    private var timestamp: Long? = null
    private var hash: ByteArray? = null
    private var signerPublicKey: BigInteger? = null
    private var signature: ByteArray? = null

    private var selfParent: ByteArray? = null
    private var otherParent: ByteArray? = null

    private var selfTransactionParent: ByteArray? = null
    private var otherTransactionParent: ByteArray? = null
    private var transactions: MutableList<HashgraphTransaction>? = null

    fun reset(): HashgraphEventBuilder {
        timestamp = null
        hash = null
        signerPublicKey = null
        signature = null
        selfParent = null
        otherParent = null
        selfTransactionParent = null
        otherTransactionParent = null
        transactions = null

        return this
    }

    fun buildFirstInHistory(keyPair: KeyPair): HashgraphEvent {
        return this
                .withSeflParent(HashgraphEvent.NO_PARENT)
                .withOtherParent(HashgraphEvent.NO_PARENT)
                .build(keyPair)
    }

    fun withSeflParent(selfParent: ByteArray): HashgraphEventBuilder {
        this.selfParent = selfParent
        return this
    }

    fun withOtherParent(otherParent: ByteArray): HashgraphEventBuilder {
        this.otherParent = otherParent
        return this
    }

    fun withTransaction(transaction: HashgraphTransaction): HashgraphEventBuilder {
        checkTransactionsInitialized()
        this.transactions!!.add(transaction)

        return this
    }

    fun withTransactions(transactions: List<HashgraphTransaction>): HashgraphEventBuilder {
        checkTransactionsInitialized()
        this.transactions!!.addAll(transactions)

        return this
    }

    fun withSelfTransactionParent(selfTransactionParent: ByteArray): HashgraphEventBuilder {
        this.selfTransactionParent = selfTransactionParent
        return this
    }

    fun withOtherTransactionParent(otherTransactionParent: ByteArray): HashgraphEventBuilder {
        this.otherTransactionParent = otherTransactionParent
        return this
    }

    fun build(keyPair: KeyPair): HashgraphEvent {
        if (!areBasicFieldsInitialized()) throw RuntimeException(
                "Unable to build hashgraph event with unknown parents.\n" +
                        "Define all fields: selfParent, otherParent."
        )
        if (!areTransactionFieldsInitializedProperly()) throw RuntimeException(
                "Unable to build hashgraph event with unproperly configured transactions.\n" +
                        "Define all fields: selfTransactionParent, otherTransactionParent, transactions."
        )

        val timestamp = Date().time

        val serializedTimestamp = SerializationUtils.anyToBytes(timestamp)
        val serializedTransactions = SerializationUtils.anyToBytes(transactions)
        val fieldsToTakeIntoAccount = arrayOf(
                selfParent,
                otherParent,
                selfTransactionParent,
                otherTransactionParent,
                serializedTransactions,
                serializedTimestamp
        )

        val hash = CryptoUtils.hash(*fieldsToTakeIntoAccount)
        val signature = CryptoUtils.sign(*fieldsToTakeIntoAccount, hash) { keyPair.private }

        val event = HashgraphEvent(
                timestamp,
                keyPair.public,
                signature,
                selfParent!!,
                otherParent!!,
                selfTransactionParent,
                otherTransactionParent,
                transactions
        )

        reset()

        return event
    }

    private fun checkTransactionsInitialized() {
        if (this.transactions == null)
            this.transactions = mutableListOf()
    }

    private fun areBasicFieldsInitialized(): Boolean {
        return this.selfParent != null && this.otherParent != null
    }

    private fun areTransactionFieldsInitializedProperly(): Boolean {
        return (this.selfTransactionParent == null && this.otherTransactionParent == null && this.transactions == null)
                || (this.selfTransactionParent != null && this.otherTransactionParent != null && this.transactions != null && this.transactions!!.isNotEmpty())
    }
}

data class HashgraphEventData(
        val event: HashgraphEvent,
        var isWitness: Boolean = false,
        var round: BigInteger = BigInteger.ZERO,
        var famous: Boolean = false,
        var vote: Boolean = false,
        var roundReceived: BigInteger = BigInteger.ZERO,
        var consensusTimestamp: Long = 0L
)

data class HashgraphEvent(
        val timestamp: Long,
        val signerPublicKey: PublicKey,
        val signature: ByteArray,

        val selfParent: ByteArray,
        val otherParent: ByteArray,

        val selfTransactionParent: ByteArray? = null,
        val otherTransactionParent: ByteArray? = null,
        val transactions: MutableList<HashgraphTransaction>? = null
) {
    companion object {
        private val logger = loggerFor<HashgraphEvent>()

        val NO_PARENT = ByteArray(0)
    }

    fun verify(): Boolean {
        val serializedTimestamp = SerializationUtils.anyToBytes(timestamp)
        val serializedTransactions = SerializationUtils.anyToBytes(transactions)
        val fieldsToTakeIntoAccount = arrayOf(
                selfParent,
                otherParent,
                selfTransactionParent,
                otherTransactionParent,
                serializedTransactions,
                serializedTimestamp
        )

        val signatureCheckPassed = CryptoUtils.verify(signature, *fieldsToTakeIntoAccount) { signerPublicKey }

        if (!signatureCheckPassed) logger.warning("Signatures don't match. Signature check fail.")
        return signatureCheckPassed
    }

    fun containsTransaction(): Boolean {
        return transactions != null && transactions.isNotEmpty()
    }

    fun hasParents(): Boolean {
        return !this.selfParent.contentEquals(HashgraphEvent.NO_PARENT) && !this.otherParent.contentEquals(HashgraphEvent.NO_PARENT)
    }

    fun hasTransactionParents(): Boolean {
        if (this.selfTransactionParent == null) return false
        if (this.otherTransactionParent == null) return false
        if (this.selfTransactionParent.contentEquals(HashgraphEvent.NO_PARENT)) return false
        if (this.otherTransactionParent.contentEquals(HashgraphEvent.NO_PARENT)) return false

        return true
    }

    fun hash(): ByteArray {
        val serializedTransactions = SerializationUtils.anyToBytes(transactions)
        val fieldsToTakeIntoAccount = arrayOf(
                selfParent,
                otherParent,
                selfTransactionParent,
                otherTransactionParent,
                serializedTransactions,
                signature,
                signerPublicKey.encoded
        )

        return CryptoUtils.hash(*fieldsToTakeIntoAccount)
    }

    fun issuerId(): BigInteger {
        return CryptoUtils.publicKeyToId(signerPublicKey)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HashgraphEvent

        if (timestamp != other.timestamp) return false
        if (signerPublicKey != other.signerPublicKey) return false
        if (!Arrays.equals(signature, other.signature)) return false
        if (!Arrays.equals(selfParent, other.selfParent)) return false
        if (!Arrays.equals(otherParent, other.otherParent)) return false
        if (!Arrays.equals(selfTransactionParent, other.selfTransactionParent)) return false
        if (!Arrays.equals(otherTransactionParent, other.otherTransactionParent)) return false
        if (transactions != other.transactions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + signerPublicKey.hashCode()
        result = 31 * result + Arrays.hashCode(signature)
        result = 31 * result + Arrays.hashCode(selfParent)
        result = 31 * result + Arrays.hashCode(otherParent)
        result = 31 * result + (selfTransactionParent?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (otherTransactionParent?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (transactions?.hashCode() ?: 0)
        return result
    }
}

data class Vote(val from: ByteArray, val vote: Boolean) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vote

        if (!Arrays.equals(from, other.from)) return false
        if (vote != other.vote) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(from)
        result = 31 * result + vote.hashCode()
        return result
    }
}

// TODO: add caching
class Hashgraph {
    // graph itself [Event hash -> Event]
    val vertices = hashMapOf<BigInteger, HashgraphEventData>()
    // heads of graph [Issuer ID -> Event]
    val heads = hashMapOf<BigInteger, HashgraphEventData>()
    // heads of graph but containing transactions [Issuer ID -> Event with transaction]
    val headsWithTransaction = hashMapOf<BigInteger, HashgraphEventData>()
    // witnesses of each round
    val witnessesOfRounds = hashMapOf<BigInteger, MutableList<HashgraphEventData>>()
    // every this round is a coin round
    val coinRoundRate = BigInteger("10")
    // votes for each witness by each of other witnesses, that should vote
    val votes = hashMapOf<BigInteger, MutableList<Vote>>()

    companion object {
        private val logger = loggerFor<Hashgraph>()
    }

    fun addEvent(event: HashgraphEvent) {
        val hash = event.hash()
        val bigIntHash = BigInteger(hash)
        if (vertices.containsKey(bigIntHash)) {
            logger.warning("Hashgraph is already contains event: $hash")
            return
        }

        // TODO: add to temporary container and commit at every sync
        require(!containsEvent(event.selfParent) && !containsEvent(event.otherParent))
        { "Unable to add event which parents are not in graph" }

        val issuerId = event.issuerId()

        // prevent forks
/*        require((!event.hasParents() && heads[issuerId] == null) || (event.selfParent.contentEquals(heads[issuerId]!!.event.hash())))
        if (event.containsTransaction())
            require((!event.hasTransactionParents() && headsWithTransaction[issuerId] == null)
                    || (event.selfTransactionParent!!.contentEquals(headsWithTransaction[issuerId]!!.event.hash())))*/

        val eventData = HashgraphEventData(event)

        divideRounds(eventData)
        decideFameBy(eventData)
        findOrderOf(eventData.round)

        heads[issuerId] = eventData // updating heads
        vertices[bigIntHash] = eventData // adding to graph

        if (event.containsTransaction())
            headsWithTransaction[issuerId] = eventData
    }

    fun containsEvent(hash: ByteArray): Boolean {
        val bigIntHash = BigInteger(hash)
        return vertices.containsKey(bigIntHash)
    }

    fun getEventData(hash: ByteArray): HashgraphEventData? {
        val bigIntHash = BigInteger(hash)
        return vertices[bigIntHash]
    }

    fun getWitnessesOf(round: BigInteger): List<HashgraphEventData> {
        return witnessesOfRounds[round] ?: listOf()
    }

    fun addWitness(witness: HashgraphEventData) {
        if (!witness.isWitness) {
            logger.warning("Event $witness is not a witness!")
            return
        }

        if (witnessesOfRounds[witness.round] == null)
            witnessesOfRounds[witness.round] = mutableListOf(witness)

        witnessesOfRounds[witness.round]!!.add(witness)
    }

    fun getEventDatas(round: BigInteger): List<HashgraphEventData> {
        return vertices.values.filter { it.round == round }
    }

    fun getLastEventDataBy(id: BigInteger): HashgraphEventData? {
        return heads[id]
    }

    fun getLastEventDataWithTransactionBy(id: BigInteger): HashgraphEventData? {
        return headsWithTransaction[id]
    }

    // true if target can reach possibleAncestor by following 0 or more parent edges
    fun isAncestor(target: HashgraphEventData, possibleAncestor: HashgraphEventData): Boolean {
        if (target == possibleAncestor) return true
        if (!target.event.hasParents()) return false

        val selfParent = getEventData(target.event.selfParent)!!
        val selfParentPathResult = isAncestor(selfParent, possibleAncestor)

        if (selfParentPathResult) return true

        val otherParent = getEventData(target.event.otherParent)!!
        return isAncestor(otherParent, possibleAncestor)
    }

    // true if target can reach possibleAncestor by following 0 or more selfParent edges
    fun isSelfAncestor(target: HashgraphEventData, possibleAncestor: HashgraphEventData): Boolean {
        if (target == possibleAncestor) return true
        if (!target.event.hasParents()) return false

        val parent = getEventData(target.event.selfParent)!!
        return isSelfAncestor(parent, possibleAncestor)
    }

    // true if I can see that event had place
    fun canSee(watcher: HashgraphEventData, target: HashgraphEventData) = isAncestor(watcher, target)

    // true if I can see that more than 2n/3 participants can see that event
    fun canStronglySee(watcher: HashgraphEventData, target: HashgraphEventData): Boolean {
        if (!canSee(watcher, target)) return false

        val eventsToCheck = mutableListOf<HashgraphEventData>()

        var roundToAdd = watcher.round
        while (true) {
            eventsToCheck.addAll(getEventDatas(roundToAdd))

            if (roundToAdd > target.round)
                roundToAdd -= BigInteger.ONE
            else if (roundToAdd > target.round)
                roundToAdd += BigInteger.ONE
            else
                break
        }

        return eventsToCheck
                .filter { canSee(it, target) }
                .filter { canSee(watcher, it) }
                .map { it.event.issuerId() }
                .toSet()
                .size > getSupermajorityOf(heads.keys)
    }

    // assigns round number to every eventdata
    fun divideRounds(target: HashgraphEventData) {
        if (target.event.hasParents()) {
            val selfParent = getEventData(target.event.selfParent)!!
            val otherParent = getEventData(target.event.otherParent)!!

            // round = max of parents rounds
            val round = if (selfParent.round > otherParent.round) selfParent.round else otherParent.round

            val allWitnessesOfRound = getWitnessesOf(round)
            val nextRound = allWitnessesOfRound.map { canStronglySee(target, it) }.count { it } > getSupermajorityOf(allWitnessesOfRound)

            if (nextRound) target.round = round + BigInteger.ONE
            else target.round = round

            target.isWitness = target.round > selfParent.round

        } else {
            target.round = BigInteger.ONE
            target.isWitness = true
        }

        if (target.isWitness) addWitness(target)
    }

    fun decideFameBy(decider: HashgraphEventData) {
        if (!decider.isWitness) {
            logger.info("Decider: $decider is not a witness, skipping...")
            return
        }

        if (decider.round <= BigInteger.ONE) {
            logger.info("Decider: $decider is a first event in history, skipping...")
            return
        }

        val unfamousWitnesses = mutableListOf<HashgraphEventData>()

        // from earliest round
        var round = BigInteger.ONE
        while (round < decider.round) {
            unfamousWitnesses.addAll(getUnfamousWitnessesOf(round))
            round += BigInteger.ONE
        }

        unfamousWitnesses.forEach { witness ->
            val diff = decider.round - witness.round

            if (diff == BigInteger.ONE) {
                val vote = Vote(decider.event.hash(), canSee(decider, witness))
                addVote(witness.event.hash(), vote)
            } else {
                if (decider.round.mod(coinRoundRate) == BigInteger.ZERO) { // coin round
                    val vote = Vote(decider.event.hash(), decider.event.issuerId().testBit(1))
                    addVote(witness.event.hash(), vote)
                } else {
                    val allVotes = getVotes(witness.event.hash())
                    val majorityVote = allVotes.count { it.vote } >= getSupermajorityOf(allVotes)
                    val majorityVotedCount = allVotes.count { it.vote == majorityVote }

                    if (majorityVotedCount >= getSupermajorityOf(heads.keys)) {
                        witness.famous = majorityVote

                        val vote = Vote(decider.event.hash(), majorityVote)
                        addVote(witness.event.hash(), vote)
                    } else {
                        val vote = Vote(decider.event.hash(), majorityVote)
                        addVote(witness.event.hash(), vote)
                    }
                }
            }
        }
    }

    fun getVotes(of: ByteArray): MutableList<Vote> {
        val bigIntHash = BigInteger(of)
        return votes[bigIntHash] ?: mutableListOf()
    }

    // votes should be added from earliest round
    fun addVote(to: ByteArray, vote: Vote) {
        val bigIntHash = BigInteger(to)
        if (votes[bigIntHash] == null)
            votes[bigIntHash] = mutableListOf()

        val previousVoteOfThisGuy = votes[bigIntHash]!!.find { it.from.contentEquals(vote.from) }
        if (previousVoteOfThisGuy != null)
            votes[bigIntHash]!!.remove(previousVoteOfThisGuy)

        votes[bigIntHash]!!.add(vote)
    }

    // cache eventually
    fun getUnfamousWitnessesOf(round: BigInteger): List<HashgraphEventData> {
        return getWitnessesOf(round).filter { !it.famous }
    }

    // cache eventually
    fun getFamousWitnessesOf(round: BigInteger): List<HashgraphEventData> {
        return getWitnessesOf(round).filter { it.famous }
    }

    // cache eventually
    fun getUnorderedEventsOfRound(round: BigInteger): List<HashgraphEventData> {
        return vertices.values
                .filter { it.round == round }
                .filter { it.roundReceived != BigInteger.ZERO }
    }

    fun getUnorderedEventsEarlierThanRound(round: BigInteger): List<HashgraphEventData> {
        return vertices.values
                .filter { it.round < round }
                .filter { it.roundReceived != BigInteger.ZERO }
    }

    fun findOrderOf(round: BigInteger) {
        if (round <= BigInteger.ONE) return

        val witnesses = getFamousWitnessesOf(round)

        val events = getUnorderedEventsOfRound(round - BigInteger.ONE)
        events.forEach { calcOrderOf(it, witnesses) }

        val previousEvents = getUnorderedEventsEarlierThanRound(round - BigInteger.ONE)
        previousEvents.forEach { calcOrderOf(it, witnesses) }
    }

    private fun calcOrderOf(event: HashgraphEventData, witnesses: List<HashgraphEventData>) {
        val canAssignOrder = witnesses.map { canSee(it, event) }.all { can -> can }

        if (canAssignOrder) {
            event.roundReceived = witnesses[0].round
            event.consensusTimestamp = witnesses.map { it.event.timestamp }.sorted()[witnesses.size / 2]
        }
    }
}

// 2n/3, n - number of participants
fun <T> getSupermajorityOf(collection: Collection<T>): Int = if (collection.size % 2 == 0 || collection.size == 1) collection.size * 2 / 3 + 1 else collection.size * 2 / 3