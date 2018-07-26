package net.stits.kademlia.node

import net.stits.kademlia.data.KAddress
import java.math.BigInteger

/**
 * Abstract structure providing an address book interface
 *
 * @param me {KAddress} - the hosts address
 * @param k {Int} - network k parameter (number of computers in network that unlikely to be offline at the same time) - usually 20
 * @param idSpaceSize {Int} - keysize in bits (or id size in bits) - using sha256 to generate keys means using idSpaceSize = 256
 *
 * Future improvements:
 * todo 1. add clusters functionality (store, find, list)
 * todo 2. remove dead nodes eventually
 */
abstract class KAddressBook(val me: KAddress, protected val k: Int = 2, protected val idSpaceSize: Int = 256) {
    /**
     * Adds node to address book
     *
     * @param node {KAddress} - address to add
     * @return {Boolean} // TODO: maybe it should be Unit/Exception mechanics instead of True/False
     */
    abstract fun addNode(node: KAddress): Boolean

    abstract fun addNodes(nodes: List<KAddress>)

    /**
     * Removes node from address book
     *
     * @param node {KAddress} - address to remove
     * @return {Boolean} // TODO: maybe it should be Unit/Exception mechanics instead of True/False
     */
    abstract fun removeNode(node: KAddress): Boolean

    /**
     * Return node by it's id
     *
     * @param id {BigInteger} - target id
     * @return {KAddress} - node KAddress or null (if not contain)
     */
    abstract fun getNodeById(id: BigInteger): KAddress?

    /**
     * Checks if the given node is already in address book
     *
     * @param id {BigInteger} - target id
     * @return {Boolean}
     */
    abstract fun containsNode(id: BigInteger): Boolean

    /**
     * Returns k or less (if there are less in address book at all) addresses with lesser xor-distance to given node
     *
     * @param id {BigInteger} - target id
     * @return {List<KAddress>}
     */
    abstract fun getClosestTo(id: BigInteger): List<KAddress>

    /**
     * Returns k or less (if there are less in address book at all) addresses with greater xor-distance to given node
     *
     * @param id {KAddress} - target id
     * @return {List<KAddress>}
     */
    abstract fun getRearestTo(id: BigInteger): List<KAddress>

    /**
     * Returns all addresses from address book
     *
     * @return {List<KAddress>}
     */
    abstract fun getFlat(): List<KAddress>
}


class KHeap(me: KAddress, k: Int = 2, idSpaceSize: Int = 256): KAddressBook(me, k, idSpaceSize) {
    private val heap = hashMapOf<BigInteger, KAddress>()

    override fun addNode(node: KAddress): Boolean {
        return if (containsNode(node.getId())) false
        else {
            heap[node.getId()] = node
            true
        }
    }

    override fun addNodes(nodes: List<KAddress>) {
        heap.putAll(nodes.map { it.getId() to it }.toMap())
    }

    override fun removeNode(node: KAddress): Boolean {
        return if (!containsNode(node.getId())) false
        else heap.remove(node.getId(), node)
    }

    override fun getNodeById(id: BigInteger): KAddress? {
        return heap[id]
    }

    override fun containsNode(id: BigInteger): Boolean {
        return heap.containsKey(id)
    }

    override fun getClosestTo(id: BigInteger): List<KAddress> {
        return getFlatSortedByDistanceTo(id).take(k)
    }

    override fun getRearestTo(id: BigInteger): List<KAddress> {
        return getFlatSortedByDistanceTo(id).takeLast(k)
    }

    override fun getFlat(): List<KAddress> {
        return heap.values.toList()
                .filter { it.getId() != me.getId() } // removing myself from result
    }

    fun getFlatSortedByDistanceTo(id: BigInteger): List<KAddress> {
        return getFlat()
                .sortedBy { it.getId().xor(id) } // sorting by distance
    }
}
