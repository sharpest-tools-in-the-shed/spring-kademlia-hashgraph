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
    private var host: String = "localhost"

    @Value("\${NODE_PORT}")
    var port: Int = 1337

    private var id: BigInteger = BigInteger.ONE

    fun getHost(): String {
        return host
    }

    fun getAddress(): Address {
        return Address(host, port)
    }

    fun getKAddress(): KAddress {
        return KAddress(getAddress(), getId())
    }

    fun getId(): BigInteger {
        if (id == BigInteger.ONE) {
            // Now generates random sha256 as id
            val md = MessageDigest.getInstance("SHA-256")
            val random = SecureRandom()
            md.update(random.nextInt().toByte())
            val digest = md.digest()

            id = BigInteger(digest)
        }

        return id
    }
}