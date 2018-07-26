package net.stits.hashgraph

import com.fasterxml.jackson.annotation.JsonIgnore
import net.stits.osen.SerializationUtils
import net.stits.osen.loggerFor
import net.stits.utils.CryptoUtils
import java.math.BigInteger
import java.security.KeyPair
import java.util.*

/**
 * Future improvements:
 * todo 1. add transactions
 * todo 2. verify events somewhere (e.g. isValid hashgraph function)
 */
class HashgraphEventBuilder {
    private var selfParent: EventId? = null
    private var otherParent: EventId? = null

    fun reset(): HashgraphEventBuilder {
        selfParent = null
        otherParent = null

        return this
    }

    fun buildGenesis(keyPair: KeyPair): HashgraphEvent {
        return this
                .withSelfParent(null)
                .withOtherParent(null)
                .build(keyPair)
    }

    fun withSelfParent(selfParentId: EventId?): HashgraphEventBuilder {
        this.selfParent = selfParentId
        return this
    }

    fun withOtherParent(otherParentId: EventId?): HashgraphEventBuilder {
        this.otherParent = otherParentId
        return this
    }

    fun withSelfParent(selfParent: HashgraphEvent): HashgraphEventBuilder {
        this.selfParent = selfParent.id()
        return this
    }

    fun withOtherParent(otherParent: HashgraphEvent): HashgraphEventBuilder {
        this.otherParent = otherParent.id()
        return this
    }

    fun build(keyPair: KeyPair): HashgraphEvent {
        val timestamp = Date().time

        val serializedTimestamp = SerializationUtils.anyToBytes(timestamp)
        val fieldsToTakeIntoAccount = arrayOf(
                selfParent?.toByteArray(),
                otherParent?.toByteArray(),
                serializedTimestamp
        )

        val hash = CryptoUtils.hash(*fieldsToTakeIntoAccount)
        val signature = BigInteger(CryptoUtils.sign(*fieldsToTakeIntoAccount, hash) { keyPair.private })

        val event = HashgraphEvent(
                timestamp,
                CryptoUtils.publicKeyToId(keyPair.public),
                signature,
                selfParent,
                otherParent
        )

        reset()

        return event
    }
}

data class HashgraphEvent(
        val timestamp: Long,
        val creatorId: CreatorId,
        val signature: Signature,

        val selfParentId: EventId?,
        val otherParentId: EventId?
) {
    companion object {
        private val logger = loggerFor<HashgraphEvent>()
    }

    fun verify(): Boolean {
        val serializedTimestamp = SerializationUtils.anyToBytes(timestamp)
        val fieldsToTakeIntoAccount = arrayOf(
                selfParentId?.toByteArray(),
                otherParentId?.toByteArray(),
                serializedTimestamp
        )
        val hash = CryptoUtils.hash(*fieldsToTakeIntoAccount)

        val signatureCheckPassed = CryptoUtils.verify(signature.toByteArray(), *fieldsToTakeIntoAccount, hash) { CryptoUtils.idToPublicKey(creatorId) }

        if (!signatureCheckPassed) logger.warning("Signatures don't match. Signature check fail.")
        return signatureCheckPassed
    }

    @JsonIgnore
    fun isGenesis(): Boolean {
        return this.selfParentId == null || this.otherParentId == null
    }

    fun id(): BigInteger {
        val fieldsToTakeIntoAccount = arrayOf(
                selfParentId?.toByteArray(),
                otherParentId?.toByteArray(),
                signature.toByteArray(),
                creatorId.toByteArray()
        )

        return BigInteger(CryptoUtils.hash(*fieldsToTakeIntoAccount))
    }
}
