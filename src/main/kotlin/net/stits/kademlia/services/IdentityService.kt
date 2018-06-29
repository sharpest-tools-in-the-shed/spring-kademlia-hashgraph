package net.stits.kademlia.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigInteger
import java.security.MessageDigest


@Service
class IdentityService {
    @Value("\${node.host}")
    private var host: String = "localhost"

    @Value("\${node.port}")
    private var port: Int = 1337

    private var id: BigInteger = BigInteger.ONE

    init {
        val md = MessageDigest.getInstance("SHA-256")
    }

    fun getHost(): String {
        return host
    }

    fun getPort(): Int {
        return port
    }

    fun getId(): BigInteger {
        return id
    }
}