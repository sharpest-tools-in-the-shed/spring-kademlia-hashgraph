package net.stits.hashgraph

import net.stits.utils.CryptoUtils
import org.junit.Test
import java.security.KeyPair
import java.util.*


class HashgraphTest {
    val PARTICIPANT_COUNT = 10
    val participants = createParticipants(PARTICIPANT_COUNT)

    @Test
    fun `first events without transactions for every participant are added properly`() {
        val hg = Hashgraph()

        participants.forEach {
            val event = HashgraphEventBuilder().buildFirstInHistory(it)

            hg.addEvent(event)
        }

        assert(hg.heads.size == PARTICIPANT_COUNT) { "incorrect number of participants" }
        assert(hg.vertices.size == PARTICIPANT_COUNT) { "incorrect number of events" }
    }

    @Test
    fun `multiple events without transaction per participant added properly`() {
        val hg = Hashgraph()
        val eventCount = 100

        val eventBuilder = HashgraphEventBuilder()

        for (i in 1..eventCount) {
            val (selfParent, otherParent) = hg.randomParentsOrNull()

            eventBuilder
                    .withSeflParent(selfParent)
                    .withOtherParent(otherParent)

            val containsTransaction = choice()
            if (containsTransaction) {
                val (selfTransactionParent, otherTransactionParent) = hg.randomTransactionParentsOrNull()
                eventBuilder
                        .withTransaction(HashgraphTransaction("test", "test"))
                        .withSelfTransactionParent(selfTransactionParent)
                        .withOtherTransactionParent(otherTransactionParent)
            }

            val event = eventBuilder.build(participants.randomOrNull()!!)

            hg.addEvent(event)
        }

        assert(hg.heads.size == PARTICIPANT_COUNT)
        assert(hg.headsWithTransaction.size == PARTICIPANT_COUNT)
        assert(hg.headsWithTransaction.values.all { it.event.containsTransaction() })
        assert(hg.vertices.size == eventCount)
    }

    private fun createParticipants(count: Int): List<KeyPair> {
        val result = arrayListOf<KeyPair>()
        for (i in 1..count) result.add(CryptoUtils.generateECDSAKeyPair())

        return result
    }
}

fun <E> List<E>.randomOrNull(): E? = if (isNotEmpty()) get(Random().nextInt(size)) else null
fun choice(): Boolean = Random().nextBoolean()

fun Hashgraph.randomParentsOrNull(): Parents {
    if (heads.keys.isEmpty()) return Parents(HashgraphEvent.NO_PARENT, HashgraphEvent.NO_PARENT)

    val randomSelfParent = heads.values.map { it.hash() }.randomOrNull()!!

    if (heads.keys.size == 1) return Parents(HashgraphEvent.NO_PARENT, randomSelfParent)

    val randomOtherParent = heads.values.map { it.hash() }.filter { !Arrays.equals(it, randomSelfParent) }.randomOrNull()!!

    return Parents(randomSelfParent, randomOtherParent)
}

fun Hashgraph.randomTransactionParentsOrNull(): Parents {
    if (headsWithTransaction.keys.isEmpty()) return Parents(HashgraphEvent.NO_PARENT, HashgraphEvent.NO_PARENT)

    val randomSelfParent = headsWithTransaction.values.map { it.hash() }.randomOrNull()!!

    if (headsWithTransaction.keys.size == 1) return Parents(HashgraphEvent.NO_PARENT, randomSelfParent)

    val randomOtherParent = headsWithTransaction.values.map { it.hash() }.filter { !Arrays.equals(it, randomSelfParent) }.randomOrNull()!!

    return Parents(randomSelfParent, randomOtherParent)
}

data class Parents(val self: ByteArray, val other: ByteArray) {
    override fun equals(b: Any?): Boolean {
        if (this === b) return true
        if (javaClass != b?.javaClass) return false

        b as Parents

        if (!Arrays.equals(self, b.self)) return false
        if (!Arrays.equals(other, b.other)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(self)
        result = 31 * result + Arrays.hashCode(other)
        return result
    }
}