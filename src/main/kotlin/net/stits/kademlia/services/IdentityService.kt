package net.stits.kademlia.services

import net.stits.kademlia.data.KAddress
import net.stits.osen.Address
import net.stits.osen.P2P
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom


@Service
class IdentityService {
    @Autowired
    lateinit var p2p: P2P

    private var host: String = "localhost"
    private var id: BigInteger? = null

    fun getHost(): String {
        return host
    }

    fun getPort(): Int {
        return p2p.listeningPort
    }

    fun getAddress(): Address {
        return Address(host, p2p.listeningPort)
    }

    fun getKAddress(): KAddress {
        return KAddress(getAddress(), getId())
    }

    fun getId(): BigInteger {
        if (id == null) {
            // Now generates random sha256 as id
            val md = MessageDigest.getInstance("SHA-256")
            val random = SecureRandom()
            md.update(random.nextInt().toByte())
            val digest = md.digest()

            id = BigInteger(digest)
        }

        return id!!
    }
}