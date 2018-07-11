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

    fun signerPublicKeyToIssuerId(): BigInteger {
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
    val vertices = hashMapOf<ByteArray, HashgraphEvent>()
    // heads of graph [Issuer ID -> Event]
    val heads = hashMapOf<BigInteger, HashgraphEvent>()
    // heads of graph but containing transactions [Issuer ID -> Event with transaction]
    val headsWithTransaction = hashMapOf<BigInteger, HashgraphEvent>()

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

        val issuerId = event.signerPublicKeyToIssuerId()

        heads[issuerId] = event // updating heads
        vertices[hash] = event // adding to graph

        if (event.containsTransaction())
            headsWithTransaction[issuerId] = event
    }

    fun getEvent(hash: ByteArray): HashgraphEvent? {
        return vertices[hash]
    }

    fun getLastEventBy(id: BigInteger): HashgraphEvent? {
        return heads[id]
    }

    fun getLastEventWithTransactionBy(id: BigInteger): HashgraphEvent? {
        return headsWithTransaction[id]
    }

    fun getSelfParents(event: HashgraphEvent): List<HashgraphEvent> {
        val parents = mutableListOf<HashgraphEvent>()

        var curEvent = event
        while (!Arrays.equals(curEvent.selfParent, ByteArray(0))) {
            curEvent = vertices[curEvent.selfParent]
                    ?: throw RuntimeException("Event ${Base64.getEncoder().encodeToString(curEvent.selfParent)} is not in graph")

            parents.add(curEvent)
        }

        return parents
    }
}