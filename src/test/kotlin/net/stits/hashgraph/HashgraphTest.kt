package net.stits.hashgraph

import net.stits.utils.CryptoUtils
import org.junit.Before
import org.junit.Test
import java.security.KeyPair


class HashgraphTest {
    val usersCount = 4
    val users = mutableListOf<KeyPair>()

    @Before
    fun init() {
        for (i in 1..usersCount) users.add(CryptoUtils.generateECDSAKeyPair())

        users.forEach { keyPair -> println("User: pub - ${keyPair.public}") }
    }

    @Test
    fun basicTest() {
        val hg = Hashgraph()
    }
}