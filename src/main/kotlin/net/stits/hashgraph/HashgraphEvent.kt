package net.stits.hashgraph

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*


// TODO: implement secure and flexible transactions (maybe use antlr)
data class HashgraphTransaction(
        val key: String,
        val value: Any?
)

data class HashgraphEvent(
        val issuerId: BigInteger,
        val selfParent: BigInteger?,
        val otherParent: BigInteger?
) {
    private val timestamp: Long = Date().time
    private val txns: MutableList<HashgraphTransaction> = mutableListOf()
    private var signature: String? = null
    var hash: BigInteger = BigInteger.ZERO

    private val md = MessageDigest.getInstance("SHA-256")
    private val mapper = ObjectMapper().registerModule(KotlinModule())

    // TODO: implement signatures
    fun isSigned() = signature != null

    fun sign() {
        assertNotSigned()

        createHash()
        signature = issuerId.toString(10)
    }

    private fun createHash() {
        assertNotSigned()

        md.update(mapper.writeValueAsBytes(txns))
        md.update(mapper.writeValueAsBytes(issuerId))
        md.update(mapper.writeValueAsBytes(selfParent))
        md.update(mapper.writeValueAsBytes(otherParent))
        md.update(mapper.writeValueAsBytes(timestamp))
        hash = BigInteger(md.digest())
    }

    // TODO: add transaction events to a different hashgraph
    fun addTransaction(txn: HashgraphTransaction) {
        assertNotSigned()

        txns.add(txn)
    }

    private fun assertNotSigned() {
        assert(!isSigned()) { "Event ${hash.toString(10)} cannot be changed" }
    }
}

class Hashgraph {
    // graph itself
    private val vertices = hashMapOf<BigInteger, HashgraphEvent>()
    // heads of graph
    private val heads = hashMapOf<BigInteger, HashgraphEvent>()

    fun addEvent(event: HashgraphEvent) {
        if (vertices.containsKey(event.hash)) return

        require(
                event.selfParent != null
                        && event.otherParent != null
                        && !vertices.containsKey(event.selfParent)
                        && !vertices.containsKey(event.otherParent)
        ) { "Unable to add event which parents are not in graph" }

        if (heads[event.issuerId]?.hash != event.hash) {
            heads[event.issuerId] = event // updating heads
            vertices[event.hash] = event // adding to graph
        }
    }

    fun getEvent(id: BigInteger): HashgraphEvent? {
        return if (!vertices.containsKey(id)) null
        else vertices[id]
    }

    fun getSelfParents(event: HashgraphEvent, count: Int = 0): List<HashgraphEvent> {
        val parents = mutableListOf<HashgraphEvent>()

        var curEvent = event
        while (curEvent.selfParent != null) {
            curEvent = vertices[curEvent.selfParent!!]
                    ?: throw RuntimeException("Event ${curEvent.selfParent!!.toString(10)} is not in graph")

            parents.add(curEvent)
        }

        return if (count > 0) parents.take(count)
        else parents
    }

    fun getSubgraphRecursiveStartFrom(event: HashgraphEvent, parents: MutableList<HashgraphEvent> = mutableListOf()): List<HashgraphEvent> {
        if (event.selfParent != null)
            getSubgraphRecursiveStartFrom(
                    getEvent(event.selfParent)!!,
                    parents
            )
        if (event.otherParent != null)
            getSubgraphRecursiveStartFrom(
                    getEvent(event.otherParent)!!,
                    parents
            )

        parents.add(event)
        return parents
    }
}