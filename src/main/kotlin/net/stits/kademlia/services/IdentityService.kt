package net.stits.kademlia.services

import net.stits.kademlia.data.KAddress
import net.stits.osen.Address
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom


@Service
class IdentityService {
    @Value("\${node.host}")
    private var host: String = "localhost"

    @Value("\${node.port}")
    private var port: Int = 1337

    private var id: BigInteger = BigInteger.ONE

    init {
        // Now generates random sha256 as id
        val md = MessageDigest.getInstance("SHA-256")
        val randomSeed = SecureRandom.getInstanceStrong().ints().findAny().asInt
        md.update(randomSeed.toByte())
        val digest = md.digest()

        id = BigInteger(digest)
    }

    fun getHost(): String {
        return host
    }

    fun getPort(): Int {
        return port
    }

    fun getAddress(): Address {
        return Address(host, port)
    }

    fun getKAddress(): KAddress {
        return KAddress(getAddress(), id)
    }

    fun getId(): BigInteger {
        return id
    }
}