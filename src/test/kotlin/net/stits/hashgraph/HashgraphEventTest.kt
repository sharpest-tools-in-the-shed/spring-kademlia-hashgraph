package net.stits.hashgraph

import net.stits.kademlia.e2e.assertThrows
import net.stits.utils.CryptoUtils
import org.junit.Test
import java.math.BigInteger
import java.util.*


class HashgraphEventTest {
    @Test
    fun `able to create genesis and non-genesis events`() {
        val issuer = CryptoUtils.generateECDSAKeyPair()

        val genesisEvent = HashgraphEvent(
                10L,
                issuer.public,
                randomBigInteger(),
                null,
                null
        )

        val nonGenesisEvent = HashgraphEvent(
                11L,
                issuer.public,
                randomBigInteger(),
                randomBigInteger(),
                randomBigInteger()
        )

        assert(genesisEvent.isGenesis()) { "Genesis event is not genesis" }
        assert(!nonGenesisEvent.isGenesis()) { "Non-genesis event is genesis" }
        assertThrows { genesisEvent.verify() }
        assertThrows { nonGenesisEvent.verify() }
    }

    @Test
    fun `able to create genesis and non-genesis events with builder`() {
        val issuer = CryptoUtils.generateECDSAKeyPair()

        val genesisEvent = HashgraphEventBuilder().buildGenesis(issuer)
        val nonGenesisEvent = HashgraphEventBuilder()
                .withOtherParent(randomBigInteger())
                .withSelfParent(randomBigInteger())
                .build(issuer)

        assert(genesisEvent.isGenesis()) { "Genesis event is not genesis" }
        assert(!nonGenesisEvent.isGenesis()) { "Non-genesis event is genesis" }
        assert(genesisEvent.verify()) { "Verify should work correctly" }
        assert(nonGenesisEvent.verify()) { "Verify should work correctly" }

        val firstGenesisId = genesisEvent.id()
        val secondGenesisId = genesisEvent.id()
        val nonGenesisId = nonGenesisEvent.id()

        assert(firstGenesisId == secondGenesisId) { "Event hashing is inconsistent" }
        assert(firstGenesisId != nonGenesisId) { "Event hashing has collisions" }

        assert(genesisEvent.signerPublicKey == issuer.public) { "Public keys are different" }
    }

    @Test
    fun `able to create multiple different events`() {
        val issuer = CryptoUtils.generateECDSAKeyPair()
        val builder = HashgraphEventBuilder()

        val events = (1..100).map { builder.buildGenesis(issuer) }
        val hashes = events.map { it.id() }

        assert(hashes.distinct().size == hashes.size) { "Some of events have same hash" }
    }
}

fun randomBigInteger(): BigInteger {
    return BigInteger.valueOf(Random().nextLong())
}