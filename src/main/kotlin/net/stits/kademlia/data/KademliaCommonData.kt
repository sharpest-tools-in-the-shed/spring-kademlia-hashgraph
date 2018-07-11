package net.stits.kademlia.data

import net.stits.osen.Address
import net.stits.osen.Package
import net.stits.osen.SerializationUtils
import net.stits.utils.CryptoUtils
import java.math.BigInteger
import java.security.KeyPair
import java.util.*


data class KAddress(private val address: Address, private val id: BigInteger) {
    fun getAddress(): Address {
        return address
    }

    fun getId(): BigInteger {
        return id
    }
}

open class KademliaBasicPayload {
    open val from: BigInteger? = null
    open val to: BigInteger? = null
}
data class DefaultPayload(override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()
data class FindNodeRequest(val id: BigInteger, override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()
data class FindNodeResponse(val id: BigInteger, val nodesToAsk: List<KAddress>, override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()
data class FindValueRequest(val id: BigInteger, override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()
data class FindValueResponse(val id: BigInteger, val value: Any? = null, val nodesToAsk: List<KAddress>? = null, override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()
data class StoreRequest(val id: BigInteger, val value: Any, override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()
data class StoreResponse(val id: BigInteger, val success: Boolean, override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()


// TODO: somehow find a way to always know sender id
data class KademliaAdditionalMetadata(val signature: ByteArray, val senderId: BigInteger) {
    companion object {
        fun create(pack: Package, provideKeyPair: () -> KeyPair): KademliaAdditionalMetadata {
            val serializedPort = SerializationUtils.anyToBytes(pack.metadata.port)
            val serializedSession = SerializationUtils.anyToBytes(pack.metadata.session)
            val serializedMessage = SerializationUtils.anyToBytes(pack.message)

            val keyPair = provideKeyPair()
            val hash = CryptoUtils.hash(serializedPort, serializedSession, serializedMessage)
            val signature = CryptoUtils.sign(serializedPort, serializedSession, serializedMessage, hash) { keyPair.private }

            return KademliaAdditionalMetadata(signature, CryptoUtils.publicKeyToId(keyPair.public))
        }

        fun verify(pack: Package): Boolean {
            val serializedAdditionalMetadata = pack.metadata.additionalMetadata
            check(serializedAdditionalMetadata != null) { "Additional metadata is empty. Cannot verify." }

            val additionalMetadata = SerializationUtils.bytesToAny<KademliaAdditionalMetadata>(serializedAdditionalMetadata!!)!!

            val signatureCheckPassed = CryptoUtils.verify(
                    additionalMetadata.signature,
                    SerializationUtils.anyToBytes(pack.metadata.port),
                    SerializationUtils.anyToBytes(pack.metadata.session),
                    SerializationUtils.anyToBytes(pack.message)
            ) { CryptoUtils.idToPublicKey(additionalMetadata.senderId) }

            if (!signatureCheckPassed) Package.logger.warning("Signatures don't match. Signature check fail.")
            return signatureCheckPassed
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KademliaAdditionalMetadata

        if (!Arrays.equals(signature, other.signature)) return false
        if (senderId != other.senderId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(signature)
        result = 31 * result + senderId.hashCode()
        return result
    }
}