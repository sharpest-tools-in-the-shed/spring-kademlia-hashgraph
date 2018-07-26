package net.stits.hashgraph

import net.stits.utils.CryptoUtils
import net.stits.utils.contentEquals
import net.stits.utils.equalsIgnoreSize
import org.junit.Test
import java.security.KeyPair

class HashgraphTest {
    val PARTICIPANT_COUNT = 10
    val participants = createParticipants(PARTICIPANT_COUNT)
    val builder = HashgraphEventBuilder()

    /**
     * Performs functional testing of as many methods as possible (hashgraph algorithm context is ignored)
     */
    @Test
    fun `every particular method works okay`() {
        `unable to add multiple genesis events`()
        `unable to add forks`()
        `events without parents are stored`()
        `getAncestors and getSelfAncestors work properly`()
        `getEvents and getWitnesses works properly`()
    }

    fun `unable to add multiple genesis events`() {
        val hg = Hashgraph()

        val issuer0 = participants[0]
        val genesisEvent01 = builder.buildGenesis(issuer0)
        val genesisEvent02 = builder.buildGenesis(issuer0)

        assert(hg.isValid(genesisEvent01))
        hg.addEvent(genesisEvent01, Round.ONE)
        assert(!hg.isValid(genesisEvent02))
        assert(!hg.isValid(genesisEvent01))

        val issuer1 = participants[1]
        val genesisEvent11 = builder.buildGenesis(issuer1)

        assert(hg.isValid(genesisEvent11))
        hg.addEvent(genesisEvent11, Round.ONE)
        assert(!hg.isValid(genesisEvent01))
        assert(!hg.isValid(genesisEvent02))

        val nonGenesisEvent01 = builder.withSelfParent(genesisEvent01).withOtherParent(genesisEvent11).build(issuer0)

        assert(hg.isValid(nonGenesisEvent01))
        hg.addEvent(nonGenesisEvent01, Round.ONE)
        assert(!hg.isValid(genesisEvent01))
        assert(!hg.isValid(genesisEvent02))
        assert(!hg.isValid(nonGenesisEvent01))

        val nonGenesisEvent11 = builder.withSelfParent(genesisEvent11).withOtherParent(nonGenesisEvent01).build(issuer1)

        assert(hg.isValid(nonGenesisEvent11))
        hg.addEvent(nonGenesisEvent11, Round.ONE)
        assert(!hg.isValid(genesisEvent01))
        assert(!hg.isValid(genesisEvent02))
        assert(!hg.isValid(genesisEvent11))
        assert(!hg.isValid(nonGenesisEvent01))
        assert(!hg.isValid(nonGenesisEvent11))
    }

    fun `unable to add forks`() {
        val hg = Hashgraph()

        val issuer2 = participants[2]
        val issuer3 = participants[3]

        val genesisEvent2 = builder.buildGenesis(issuer2)
        val genesisEvent3 = builder.buildGenesis(issuer3)

        val nonGenesisEvent20 = builder.withSelfParent(genesisEvent2).withOtherParent(genesisEvent3).build(issuer2)
        val nonGenesisEvent21 = builder.withSelfParent(genesisEvent2).withOtherParent(genesisEvent3).build(issuer2)

        assert(hg.isValid(genesisEvent2))
        hg.addEvent(genesisEvent2, Round.ONE)
        assert(hg.isValid(genesisEvent3))
        hg.addEvent(genesisEvent3, Round.ONE)

        assert(hg.isValid(nonGenesisEvent20))
        assert(hg.isValid(nonGenesisEvent21))
        hg.addEvent(nonGenesisEvent20, Round.ONE)
        assert(!hg.isValid(nonGenesisEvent21))

        val nonGenesisEvent22 = builder.withSelfParent(nonGenesisEvent20).withOtherParent(genesisEvent3).build(issuer2)
        assert(hg.isValid(nonGenesisEvent22))
    }

    fun `events without parents are stored`() {
        val hg = Hashgraph()

        val issuer4 = participants[4]
        val issuer5 = participants[5]

        val genesisEvent4 = builder.buildGenesis(issuer4)
        val genesisEvent5 = builder.buildGenesis(issuer5)

        hg.addEvent(genesisEvent4, Round.ONE)
        hg.addEvent(genesisEvent5, Round.ONE)

        val eventWithoutBothParents = builder.withSelfParent(EventId.ONE).withOtherParent(EventId.TEN).build(issuer4)
        val eventWithoutSelfParentOtherParentNull = builder.withSelfParent(EventId.ONE).build(issuer4)
        val eventWithoutOtherParentSelfParentNull = builder.withOtherParent(EventId.TEN).build(issuer4)
        val eventWithoutSelfParentOtherParentPresent = builder.withSelfParent(EventId.ONE).withOtherParent(genesisEvent5).build(issuer4)
        val eventWithoutOtherParentSelfParentPresent = builder.withSelfParent(genesisEvent4).withOtherParent(EventId.TEN).build(issuer4)

        assert(hg.getEventsWithoutParents().isEmpty()) { "Somehow there are already events without parents" }

        assert(!hg.isValid(eventWithoutBothParents))
        assert(!hg.isValid(eventWithoutOtherParentSelfParentNull))
        assert(!hg.isValid(eventWithoutOtherParentSelfParentPresent))
        assert(!hg.isValid(eventWithoutSelfParentOtherParentNull))
        assert(!hg.isValid(eventWithoutSelfParentOtherParentPresent))

        assert(hg.getEventsWithoutParents().size == 3) { "There should be only 3 appropriate events" }
    }

    fun `getAncestors and getSelfAncestors work properly`() {
        val hg = Hashgraph()

        val issuer0 = participants[0]
        val issuer1 = participants[1]

        val genesisEvent0 = builder.buildGenesis(issuer0)
        val genesisEvent1 = builder.buildGenesis(issuer1)

        val event00 = builder.withSelfParent(genesisEvent0).withOtherParent(genesisEvent1).build(issuer0)
        val event10 = builder.withSelfParent(genesisEvent1).withOtherParent(genesisEvent0).build(issuer1)
        val event01 = builder.withSelfParent(event00).withOtherParent(event10).build(issuer0)
        val event11 = builder.withSelfParent(event10).withOtherParent(event01).build(issuer1)
        val event02 = builder.withSelfParent(event01).withOtherParent(event11).build(issuer0)
        val event12 = builder.withSelfParent(event11).withOtherParent(event01).build(issuer1)

        hg.addEvent(genesisEvent0, Round.ONE)
        hg.addEvent(genesisEvent1, Round.ONE)
        hg.addEvent(event00, Round.ONE)
        hg.addEvent(event10, Round.ONE)
        hg.addEvent(event01, Round.ONE)
        hg.addEvent(event11, Round.ONE)
        hg.addEvent(event02, Round.ONE)
        hg.addEvent(event12, Round.ONE)

        val ancestors = hg.getAncestors(event02)
        assert(
                ancestors.contentEquals(listOf(genesisEvent0, genesisEvent1, event00, event01, event11, event10).map { it.id() })
        ) { "Event has invalid ancestors" }

        val selfAncestors = hg.getSelfAncestors(event02)
        assert(
                selfAncestors.contentEquals(listOf(genesisEvent0, event00, event01).map { it.id() })
        ) { "Event has invalid self-ancestors" }
    }

    fun `getEvents and getWitnesses works properly`() {
        val hg = Hashgraph()
        val issuer0 = participants[0]
        val issuer1 = participants[1]

        val genesisEvent0 = builder.buildGenesis(issuer0)
        val genesisEvent1 = builder.buildGenesis(issuer1)

        val event00 = builder.withSelfParent(genesisEvent0).withOtherParent(genesisEvent1).build(issuer0)
        val event10 = builder.withSelfParent(genesisEvent1).withOtherParent(genesisEvent0).build(issuer1)
        val event01 = builder.withSelfParent(event00).withOtherParent(event10).build(issuer0)
        val event11 = builder.withSelfParent(event10).withOtherParent(event00).build(issuer1)
        val event02 = builder.withSelfParent(event01).withOtherParent(event11).build(issuer0)
        val event12 = builder.withSelfParent(event11).withOtherParent(event01).build(issuer1)

        val ONE = Round.ONE
        val TWO = ONE + ONE
        val THREE = TWO + ONE
        val FOUR = THREE + ONE

        hg.addEvent(genesisEvent0, ONE)
        hg.addEvent(genesisEvent1, ONE)
        hg.addEvent(event00, TWO)
        hg.addEvent(event10, TWO)
        hg.addEvent(event01, THREE)
        hg.addEvent(event11, THREE)
        hg.addEvent(event02, FOUR)
        hg.addEvent(event12, FOUR)

        assert(
                hg.getEvents().contentEquals(
                        listOf(genesisEvent0, genesisEvent1, event00, event10, event01, event02, event11, event12)
                )
        ) { "Hashgraph contains invalid events" }

        assert(
                hg.getEvents(ONE).contentEquals(listOf(genesisEvent0, genesisEvent1)) &&
                        hg.getEvents(FOUR).contentEquals(listOf(event02, event12))
        ) { "Getting events by round is invalid" }

        assert(
                hg.getEvents(TWO, FOUR).contentEquals(
                        listOf(event00, event10, event01, event11, event02, event12)
                )
        ) { "Getting events by round range is invalid" }

        hg.addWitness(genesisEvent0, ONE)
        hg.addWitness(genesisEvent1, ONE)
        hg.addWitness(event00, TWO)
        hg.addWitness(event01, TWO)
        hg.addWitness(event10, THREE)
        hg.addWitness(event11, THREE)

        assert(
                hg.getUnFamousWitnesses().contentEquals(
                        listOf(genesisEvent0, genesisEvent1, event00, event10, event01, event11).map { it.id() }
                )
        ) { "Getting unfamous witnesses works faulty" }

        assert(hg.getFamousWitnesses().isEmpty()) { "Hashgraph somehow contains famous witnesses" }

        assert(
                hg.getUnFamousWitnesses(ONE).contentEquals(
                        listOf(genesisEvent0, genesisEvent1).map { it.id() }
                )
        ) { "Getting unfamous witnesses by round works faulty" }

        assert(
                hg.getUnFamousWitnesses(ONE, THREE).contentEquals(hg.getUnFamousWitnesses())
        ) { "Getting unfamous witnesses by round range works faulty" }

        hg.makeWitnessFamous(genesisEvent0)
        hg.makeWitnessFamous(event11)

        assert(
                hg.getUnFamousWitnesses().contentEquals(
                        listOf(genesisEvent1, event00, event10, event01).map { it.id() }
                )
        ) { "Making witness famous works faulty" }

        assert(
                hg.getFamousWitnesses().contentEquals(
                        listOf(genesisEvent0, event11).map { it.id() }
                )
        ) { "Getting famous witnesses works faulty" }

        assert(
                hg.getFamousWitnesses(ONE).contentEquals(
                        listOf(genesisEvent0).map { it.id() }
                )
        ) { "Getting famous witnesses by round works faulty" }

        assert(
                hg.getFamousWitnesses(ONE, THREE).contentEquals(hg.getFamousWitnesses())
        ) { "Getting famous witnesses by round range works faulty" }

        assert(
                hg.getAllWitnesses(ONE, THREE).contentEquals(
                        hg.getFamousWitnesses(ONE, THREE) + hg.getUnFamousWitnesses(ONE, THREE)
                )
        ) { "Getting all witnesses by round range works faulty" }
    }

    /**
     * This test recreates situation showed in https://www.swirlds.com/downloads/SWIRLDS-TR-2016-02.pdf
     * and ensures that everything works as expected
     */
    @Test
    fun `hashgraph works properly`() {
        val hg = Hashgraph()
        val (A, B, C, D) = participants

        // round 1
        val A1 = builder.buildGenesis(A)
        val B1 = builder.buildGenesis(B)
        val C1 = builder.buildGenesis(C)
        val D1 = builder.buildGenesis(D)
        val D11 = builder.withSelfParent(D1).withOtherParent(B1).build(D)
        val B11 = builder.withSelfParent(B1).withOtherParent(D11).build(B)
        val D12 = builder.withSelfParent(D11).withOtherParent(B11).build(D)
        val B12 = builder.withSelfParent(B11).withOtherParent(C1).build(B)
        val A11 = builder.withSelfParent(A1).withOtherParent(B11).build(A)
        val D13 = builder.withSelfParent(D12).withOtherParent(B12).build(D)
        val C11 = builder.withSelfParent(C1).withOtherParent(B12).build(C)
        val B13 = builder.withSelfParent(B12).withOtherParent(D13).build(B)

        // round 2
        val D2 = builder.withSelfParent(D13).withOtherParent(A11).build(D)
        val A2 = builder.withSelfParent(A11).withOtherParent(D2).build(A)
        val B2 = builder.withSelfParent(B13).withOtherParent(D2).build(B)
        val A21 = builder.withSelfParent(A2).withOtherParent(C11).build(A)
        val C2 = builder.withSelfParent(C11).withOtherParent(A21).build(C)
        val D21 = builder.withSelfParent(D2).withOtherParent(B2).build(D)
        val A22 = builder.withSelfParent(A21).withOtherParent(B2).build(A)
        val B21 = builder.withSelfParent(B2).withOtherParent(A22).build(B)
        val D22 = builder.withSelfParent(D21).withOtherParent(A22).build(D)

        // round 3
        val B3 = builder.withSelfParent(B21).withOtherParent(D22).build(B)
        val A3 = builder.withSelfParent(A22).withOtherParent(B3).build(A)
        val D3 = builder.withSelfParent(D22).withOtherParent(B3).build(D)
        val D31 = builder.withSelfParent(D3).withOtherParent(C2).build(D)
        val C3 = builder.withSelfParent(C2).withOtherParent(D31).build(C)
        val B31 = builder.withSelfParent(B3).withOtherParent(A3).build(B)
        val B32 = builder.withSelfParent(B31).withOtherParent(A3).build(B)
        val A31 = builder.withSelfParent(A3).withOtherParent(B32).build(A)
        val B33 = builder.withSelfParent(B32).withOtherParent(D31).build(B)
        val A32 = builder.withSelfParent(A31).withOtherParent(B33).build(A)
        val B34 = builder.withSelfParent(B33).withOtherParent(A32).build(B)
        val D32 = builder.withSelfParent(D31).withOtherParent(B33).build(D)

        // round 4
        val D4 = builder.withSelfParent(D32).withOtherParent(C3).build(D)
        val B4 = builder.withSelfParent(B34).withOtherParent(D4).build(B)

        val events = listOf(
                A1, B1, C1, D1, D11, B11, D12, B12, A11, D13, C11, B13, D2, A2, B2, A21, C2, D21, A22, B21, D22, B3, A3,
                D3, D31, C3, B31, B32, A31, B33, A32, B34, D32, D4, B4
        )

        events.forEach { hg.processEvent(it) }

        assert(
                hg.getAllWitnesses().contentEquals(
                        listOf(A1, A2, A3, B1, B2, B3, B4, C1, C2, C3, D1, D2, D3, D4).map { it.id() }
                )
        ) { "Witnesses are incorrect" }

        assert(
                hg.getFamousWitnesses().contentEquals(
                        listOf(A1, B1, C1, D1, A2, B2, C2, D2).map { it.id() }
                )
        ) { "Famous witnesses are incorrect" }

        assert(
                hg.getUnFamousWitnesses().contentEquals(
                        listOf(A3, B3, C3, D3, D4, B4).map { it.id() }
                )
        ) { "Unfamous witnesses are incorrect" }

        assert(
                hg.getConsensusEvents().contentEquals(
                        listOf(A1, B1, C1, D1, A2, D2, C11, A21, B11, B12, A11, D11, D12, D13).map { it.id() }
                )
        ) { "Incorrect ordered events" }
    }

    @Test
    fun `order of adding events doesn't matter`() {
        val (A, B, C, D) = participants

        // round 1
        val A1 = builder.buildGenesis(A)
        val B1 = builder.buildGenesis(B)
        val C1 = builder.buildGenesis(C)
        val D1 = builder.buildGenesis(D)
        val D11 = builder.withSelfParent(D1).withOtherParent(B1).build(D)
        val B11 = builder.withSelfParent(B1).withOtherParent(D11).build(B)
        val D12 = builder.withSelfParent(D11).withOtherParent(B11).build(D)
        val B12 = builder.withSelfParent(B11).withOtherParent(C1).build(B)
        val A11 = builder.withSelfParent(A1).withOtherParent(B11).build(A)
        val D13 = builder.withSelfParent(D12).withOtherParent(B12).build(D)
        val C11 = builder.withSelfParent(C1).withOtherParent(B12).build(C)
        val B13 = builder.withSelfParent(B12).withOtherParent(D13).build(B)

        // round 2
        val D2 = builder.withSelfParent(D13).withOtherParent(A11).build(D)
        val A2 = builder.withSelfParent(A11).withOtherParent(D2).build(A)
        val B2 = builder.withSelfParent(B13).withOtherParent(D2).build(B)
        val A21 = builder.withSelfParent(A2).withOtherParent(C11).build(A)
        val C2 = builder.withSelfParent(C11).withOtherParent(A21).build(C)
        val D21 = builder.withSelfParent(D2).withOtherParent(B2).build(D)
        val A22 = builder.withSelfParent(A21).withOtherParent(B2).build(A)
        val B21 = builder.withSelfParent(B2).withOtherParent(A22).build(B)
        val D22 = builder.withSelfParent(D21).withOtherParent(A22).build(D)

        // round 3
        val B3 = builder.withSelfParent(B21).withOtherParent(D22).build(B)
        val A3 = builder.withSelfParent(A22).withOtherParent(B3).build(A)
        val D3 = builder.withSelfParent(D22).withOtherParent(B3).build(D)
        val D31 = builder.withSelfParent(D3).withOtherParent(C2).build(D)
        val C3 = builder.withSelfParent(C2).withOtherParent(D31).build(C)
        val B31 = builder.withSelfParent(B3).withOtherParent(A3).build(B)
        val B32 = builder.withSelfParent(B31).withOtherParent(A3).build(B)
        val A31 = builder.withSelfParent(A3).withOtherParent(B32).build(A)
        val B33 = builder.withSelfParent(B32).withOtherParent(D31).build(B)
        val A32 = builder.withSelfParent(A31).withOtherParent(B33).build(A)
        val B34 = builder.withSelfParent(B33).withOtherParent(A32).build(B)
        val D32 = builder.withSelfParent(D31).withOtherParent(B33).build(D)

        // round 4
        val D4 = builder.withSelfParent(D32).withOtherParent(C3).build(D)
        val B4 = builder.withSelfParent(B34).withOtherParent(D4).build(B)

        val events1 = listOf(
                A1, B1, C1, D1, D11, B11, D12, B12, A11, D13, C11, B13, D2, A2, B2, A21, C2, D21, A22, B21, D22, B3, A3,
                D3, D31, C3, B31, B32, A31, B33, A32, B34, D32, D4, B4
        )
        val events2 = listOf(
                D3, D31, C3, B31, B32, A31, B33, A32, B34, D32, D4, B4,
                A1, B1, C1, D1, D11, B11, D12, B12, A11, D13, C11, B13, D2, A2, B2, A21, C2, D21, A22, B21, D22, B3, A3
        )

        val hg1 = Hashgraph()
        val hg2 = Hashgraph()

        events1.forEach { hg1.processEvent(it) }
        events2.forEach { hg2.processEvent(it) }

        assert(hg1.getConsensusEvents() == hg2.getConsensusEvents()) { "Immunity to different order of adding events doesnt' work" }
        assert(hg2.getEventsWithoutParents().isEmpty()) { "eventsWithoutParents do not clean itself" }
    }

    @Test
    fun `multiple hashgraphs have consistent state no matter of moment of time`() {
        val (A, B, C, D) = participants

        // round 1
        val A1 = builder.buildGenesis(A)
        val B1 = builder.buildGenesis(B)
        val C1 = builder.buildGenesis(C)
        val D1 = builder.buildGenesis(D)
        val D11 = builder.withSelfParent(D1).withOtherParent(B1).build(D)
        val B11 = builder.withSelfParent(B1).withOtherParent(D11).build(B)
        val D12 = builder.withSelfParent(D11).withOtherParent(B11).build(D)
        val B12 = builder.withSelfParent(B11).withOtherParent(C1).build(B)
        val A11 = builder.withSelfParent(A1).withOtherParent(B11).build(A)
        val D13 = builder.withSelfParent(D12).withOtherParent(B12).build(D)
        val C11 = builder.withSelfParent(C1).withOtherParent(B12).build(C)
        val B13 = builder.withSelfParent(B12).withOtherParent(D13).build(B)

        // round 2
        val D2 = builder.withSelfParent(D13).withOtherParent(A11).build(D)
        val A2 = builder.withSelfParent(A11).withOtherParent(D2).build(A)
        val B2 = builder.withSelfParent(B13).withOtherParent(D2).build(B)
        val A21 = builder.withSelfParent(A2).withOtherParent(C11).build(A)
        val C2 = builder.withSelfParent(C11).withOtherParent(A21).build(C)
        val D21 = builder.withSelfParent(D2).withOtherParent(B2).build(D)
        val A22 = builder.withSelfParent(A21).withOtherParent(B2).build(A)
        val B21 = builder.withSelfParent(B2).withOtherParent(A22).build(B)
        val D22 = builder.withSelfParent(D21).withOtherParent(A22).build(D)

        // round 3
        val B3 = builder.withSelfParent(B21).withOtherParent(D22).build(B)
        val A3 = builder.withSelfParent(A22).withOtherParent(B3).build(A)
        val D3 = builder.withSelfParent(D22).withOtherParent(B3).build(D)
        val D31 = builder.withSelfParent(D3).withOtherParent(C2).build(D)
        val C3 = builder.withSelfParent(C2).withOtherParent(D31).build(C)
        val B31 = builder.withSelfParent(B3).withOtherParent(A3).build(B)
        val B32 = builder.withSelfParent(B31).withOtherParent(A3).build(B)
        val A31 = builder.withSelfParent(A3).withOtherParent(B32).build(A)
        val B33 = builder.withSelfParent(B32).withOtherParent(D31).build(B)
        val A32 = builder.withSelfParent(A31).withOtherParent(B33).build(A)
        val B34 = builder.withSelfParent(B33).withOtherParent(A32).build(B)
        val D32 = builder.withSelfParent(D31).withOtherParent(B33).build(D)

        // round 4
        val D4 = builder.withSelfParent(D32).withOtherParent(C3).build(D)
        val B4 = builder.withSelfParent(B34).withOtherParent(D4).build(B)

        val events1 = listOf(
                A1, B1, C1, D1, D11, B11, D12, B12, A11, D13, C11, B13, D2, A2, B2, A21, C2, D21, A22, B21, D22, B3, A3,
                D3, D31, C3, B31, B32, A31, B33, A32, B34, D32, D4, B4
        )
        val events2 = listOf(
                A1, B1, C1, B11, D1, D11, D12, B12, A11, D13, B13, D2, A2, B2, C11, A21, C2, D21, A22, B21, D22, A3, B3,
                D3, D31
        )

        val hg1 = Hashgraph()
        val hg2 = Hashgraph()

        events1.forEach { hg1.processEvent(it) }
        events2.forEach { hg2.processEvent(it) }

        assert(hg2.getConsensusEvents().equalsIgnoreSize(hg1.getConsensusEvents())) { "Hashgraphs are inconsistent" }
    }
}



// TODO: test if cheater appears

private fun createParticipants(count: Int): List<KeyPair> {
    val result = arrayListOf<KeyPair>()
    for (i in 1..count) result.add(CryptoUtils.generateECDSAKeyPair())

    return result
}