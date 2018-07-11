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

        participants.forEach {
            val event = eventBuilder.buildFirstInHistory(it)

            hg.addEvent(event)
        }

        for (i in 1..eventCount) {
            val randomSelfParent = hg.heads.values.map { it.hash() }.randomOrNull()!!
            val randomOtherParent = hg.heads.values.map { it.hash() }.filter { !Arrays.equals(it, randomSelfParent) }.randomOrNull()!!

            val event = eventBuilder
                    .withSeflParent(randomSelfParent)
                    .withOtherParent(randomOtherParent)

            val containsTransaction = choice()

            if (containsTransaction) {
                val randomSelfTransactionParent = hg.headsWithTransaction.values
                event.withTransaction(HashgraphTransaction("test", "test"))
            }
        }
    }

    private fun createParticipants(count: Int): List<KeyPair> {
        val result = arrayListOf<KeyPair>()
        for (i in 1..count) result.add(CryptoUtils.generateECDSAKeyPair())

        return result
    }
}

fun <E> List<E>.randomOrNull(): E? = if (isNotEmpty()) get(Random().nextInt(size)) else null
fun choice(): Boolean = Random().nextBoolean()