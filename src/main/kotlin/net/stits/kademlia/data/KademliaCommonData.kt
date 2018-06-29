package net.stits.kademlia.data

import net.stits.osen.Address
import java.math.BigInteger


data class KAddress(private val address: Address, private val id: BigInteger) {
    fun getBitAtIndex(bitIndex: Int, bitSpace: Int): Int {
        return if (id.testBit((bitSpace - 1) - bitIndex)) 1 else 0
    }

    fun getAddress(): Address {
        return address
    }

    fun getHost(): String {
        return address.host
    }

    fun getPort(): Int {
        return address.port
    }

    fun getId(): BigInteger {
        return id
    }
}

abstract class KademliaBasicPayload {
    abstract val from: BigInteger
    abstract val to: BigInteger
}
data class DefaultPayload(override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()
data class FindNodeRequest(val id: BigInteger, override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()
data class FindNodeResponse(val id: BigInteger, val nodesToAsk: List<KAddress>, override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()
data class FindValueRequest(val id: BigInteger, override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()
data class FindValueResponse(val id: BigInteger, val value: ByteArray? = null, val nodesToAsk: List<KAddress>? = null, override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()
data class StoreRequest(val id: BigInteger, val value: ByteArray, override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()
data class StoreResponse(val id: BigInteger, val success: Boolean, override val from: BigInteger, override val to: BigInteger): KademliaBasicPayload()