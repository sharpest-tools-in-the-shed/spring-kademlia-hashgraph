package net.stits.kademlia.data

import net.stits.osen.Address
import java.math.BigInteger


data class KAddress(private val address: Address, private val id: BigInteger) {

    fun getAddress() = address

    fun getId() = id
}

open class KademliaBasicPayload {
    open val from: BigInteger? = null
    open val to: BigInteger? = null
}

data class DefaultPayload(override val from: BigInteger, override val to: BigInteger) : KademliaBasicPayload()

data class FindNodeRequest(val id: BigInteger, override val from: BigInteger, override val to: BigInteger) :
    KademliaBasicPayload()

data class FindNodeResponse(
    val id: BigInteger,
    val nodesToAsk: List<KAddress>,
    override val from: BigInteger,
    override val to: BigInteger
) : KademliaBasicPayload()

data class FindValueRequest(val id: BigInteger, override val from: BigInteger, override val to: BigInteger) :
    KademliaBasicPayload()

data class FindValueResponse(
    val id: BigInteger,
    val value: Any? = null,
    val nodesToAsk: List<KAddress>? = null,
    override val from: BigInteger,
    override val to: BigInteger
) : KademliaBasicPayload()

data class StoreRequest(
    val id: BigInteger,
    val value: Any,
    override val from: BigInteger,
    override val to: BigInteger
) : KademliaBasicPayload()

data class StoreResponse(
    val id: BigInteger,
    val success: Boolean,
    override val from: BigInteger,
    override val to: BigInteger
) : KademliaBasicPayload()