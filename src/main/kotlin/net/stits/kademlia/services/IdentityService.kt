package net.stits.kademlia.services

import net.stits.kademlia.data.KAddress
import net.stits.osen.Address
import net.stits.osen.P2P
import net.stits.utils.CryptoUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.security.KeyPair


@Service
class IdentityService {
    @Autowired
    lateinit var p2p: P2P

    private val host: String = "localhost"
    private val keyPair: KeyPair = CryptoUtils.generateECDSAKeyPair()

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

    fun getKeyPair(): KeyPair {
        return keyPair
    }

    fun getId(): BigInteger {
        return CryptoUtils.publicKeyToId(keyPair.public)
    }
}