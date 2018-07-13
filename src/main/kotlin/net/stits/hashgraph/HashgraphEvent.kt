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
        var vote: Boolean = false
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

    fun hasSelfParent(): Boolean {
        return !this.selfParent.contentEquals(HashgraphEvent.NO_PARENT)
    }

    fun hasOtherParent(): Boolean {
        return !this.otherParent.contentEquals(HashgraphEvent.NO_PARENT)
    }

    fun hash(): ByteArray {
        val serializedTransactions = SerializationUtils.anyToBytes(transactions)
        val fieldsToTakeIntoAccount = arrayOf(
                selfParent,
                otherParent,
                selfTransactionParent,
                otherTransactionParent,
                serializedTransactions
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

class Hashgraph {
    // graph itself [Event hash -> Event]
    val vertices = hashMapOf<ByteArray, HashgraphEventData>()
    // heads of graph [Issuer ID -> Event]
    val heads = hashMapOf<BigInteger, HashgraphEventData>()
    // heads of graph but containing transactions [Issuer ID -> Event with transaction]
    val headsWithTransaction = hashMapOf<BigInteger, HashgraphEventData>()

    val coinRoundRate = BigInteger("10")

    companion object {
        private val logger = loggerFor<Hashgraph>()
    }

    fun addEvent(event: HashgraphEvent) {
        val hash = event.hash()
        if (vertices.containsKey(hash)) {
            logger.warning("Hashgraph is already contains event: $hash")
            return
        }

        require(
                !vertices.containsKey(event.selfParent) && !vertices.containsKey(event.otherParent)
        ) { "Unable to add event which parents are not in graph" }

        val issuerId = event.issuerId()

        // prevent forks
        require(event.selfParent.contentEquals(heads[issuerId]!!.event.hash()))
        if (event.containsTransaction())
            require(event.selfTransactionParent!!.contentEquals(headsWithTransaction[issuerId]!!.event.hash()))

        val eventData = HashgraphEventData(event)

        heads[issuerId] = eventData // updating heads
        vertices[hash] = eventData // adding to graph

        if (event.containsTransaction())
            headsWithTransaction[issuerId] = eventData
    }

    fun getEventData(hash: ByteArray): HashgraphEventData? {
        return vertices[hash]
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

        if (target.event.hasSelfParent()) {
            val parent = getEventData(target.event.selfParent)!!
            return isAncestor(parent, possibleAncestor)
        }

        if (target.event.hasOtherParent()) {
            val parent = getEventData(target.event.otherParent)!!
            return isAncestor(parent, possibleAncestor)
        }

        return false
    }

    // true if target can reach possibleAncestor by following 0 or more selfParent edges
    fun isSelfAncestor(target: HashgraphEventData, possibleAncestor: HashgraphEventData): Boolean {
        if (target == possibleAncestor) return true

        if (target.event.hasSelfParent()) {
            val parent = getEventData(target.event.selfParent)!!
            return isSelfAncestor(parent, possibleAncestor)
        }

        return false
    }

    // true if I can see that event had place
    fun canSee(watcher: HashgraphEventData, target: HashgraphEventData) = isAncestor(watcher, target)

    // true if I can see that more than 2n/3 participants can see that event
    fun canStronglySee(watcher: HashgraphEventData, target: HashgraphEventData): Boolean {
        if (!canSee(watcher, target)) return false

        return vertices.values
                .filter { canSee(it, target) }
                .filter { canSee(watcher, it) }
                .map { it.event.issuerId() }
                .toSet()
                .size > getSupermajorityOf(heads.keys)
    }

    // assigns round number to every eventdata
    fun divideRounds(target: HashgraphEventData) {
        val hasSelfParent = target.event.hasSelfParent()
        val hasOtherParent = target.event.hasOtherParent()

        if (hasSelfParent) {
            val parent = getEventData(target.event.selfParent)!!
            divideRounds(parent)
        }

        if (hasOtherParent) {
            val parent = getEventData(target.event.otherParent)!!
            divideRounds(parent)
        }

        if (hasSelfParent && hasOtherParent) {
            val selfParent = getEventData(target.event.selfParent)!!
            val otherParent = getEventData(target.event.otherParent)!!

            // round = max of parents rounds
            val round = if (selfParent.round > otherParent.round) selfParent.round else otherParent.round

            val allWitnessesOfRound = vertices.values.filter { it.round == round && it.isWitness }
            val nextRound = allWitnessesOfRound.map { canStronglySee(target, it) }.count { it } > getSupermajorityOf(allWitnessesOfRound)

            if (nextRound) target.round = round + BigInteger.ONE
            else target.round = round

            target.isWitness = target.round > selfParent.round
        }

        if (!hasSelfParent && !hasOtherParent) {
            target.round = BigInteger.ONE
            target.isWitness = true
        }
    }

    fun decideFame() {
        val sortedByRoundEventDatas = vertices.values.sortedBy { it.round }

        x@ sortedByRoundEventDatas.forEach { x ->
            x.famous = false

            y@ sortedByRoundEventDatas.forEach { y ->
                if (x.isWitness && y.isWitness && y.round > x.round) {
                    val diff = y.round - x.round
                    val allWitnessesOfRoundThatYCanStronglySee = vertices.values
                            .filter { it.round == y.round - BigInteger.ONE }
                            .filter { it.isWitness }
                            .filter { canStronglySee(y, it) }
                    val majorityVote = allWitnessesOfRoundThatYCanStronglySee
                            .map { it.vote }
                            .count { it } >= allWitnessesOfRoundThatYCanStronglySee.size / 2
                    val majorityVoteCount = allWitnessesOfRoundThatYCanStronglySee
                            .map { it.vote }
                            .count { it == majorityVote }

                    if (diff == BigInteger.ONE) {
                        y.vote = canSee(y, x)
                    } else {
                        if (diff.mod(coinRoundRate) !== BigInteger.ZERO) {
                            y.vote = majorityVote

                            if (majorityVoteCount > allWitnessesOfRoundThatYCanStronglySee.size * 2 / 3) {
                                x.famous = majorityVote
                                return@y
                            }
                        } else {
                            if (majorityVoteCount > allWitnessesOfRoundThatYCanStronglySee.size * 2 / 3)
                                y.vote = majorityVote
                            else
                                y.vote = y.event.issuerId().testBit(1) // not middle bit of signature, but okay too; we can use secure random here
                        }
                    }
                }
            }
        }
    }

    fun findOrder() {
        vertices.values.forEach {
            val groupedByRound = vertices.values.associateBy { it.round }
            groupedByRound.filter { /* THIS IS TOO HARD OMG THAT WHITEPAPER */ }
        }
    }
}

// 2n/3, n - number of participants
fun <T> getSupermajorityOf(collection: Collection<T>): Int
        = if (collection.size % 2 == 0 || collection.size == 1) collection.size * 2 / 3 + 1 else collection.size * 2 / 3