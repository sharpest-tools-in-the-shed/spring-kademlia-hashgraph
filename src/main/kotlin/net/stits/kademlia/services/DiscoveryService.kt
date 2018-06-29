package net.stits.kademlia.services

import net.stits.ID_SPACE_SIZE
import net.stits.K_PARAMETER
import net.stits.kademlia.data.KAddress
import net.stits.kademlia.node.KAddressBook
import net.stits.kademlia.node.KHeap
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigInteger


@Service
class DiscoveryService {
    @Autowired
    lateinit var identityService: IdentityService

    var addressBook: KAddressBook? = null

    private fun initAddressBook() {
        addressBook = KHeap(identityService.getKAddress(), K_PARAMETER, ID_SPACE_SIZE)
    }

    fun addNode(address: KAddress): Boolean {
        if (addressBook == null) initAddressBook()

        return addressBook!!.addNode(address)
    }

    fun addNodes(addresses: List<KAddress>) {
        if (addressBook == null) initAddressBook()

        return addressBook!!.addNodes(addresses)
    }

    fun toList(): List<KAddress> {
        if (addressBook == null) initAddressBook()

        return addressBook!!.getFlat()
    }

    fun addNewNodes(nodes: List<KAddress>): List<KAddress> {
        val notAskedNodes = nodes.filter { !containsId(it.getId()) }
        addNodes(nodes)
        return notAskedNodes
    }

    fun removeNode(address: KAddress): Boolean {
        if (addressBook == null) initAddressBook()

        return addressBook!!.removeNode(address)
    }

    fun getClosest(id: BigInteger): KAddress? {
        if (addressBook == null) initAddressBook()

        return addressBook!!.getClosestTo(id).firstOrNull()
    }

    fun getClosestKBucket(id: BigInteger): List<KAddress> {
        if (addressBook == null) initAddressBook()

        return addressBook!!.getClosestTo(id)
    }

    fun containsId(id: BigInteger): Boolean {
        if (addressBook == null) initAddressBook()

        return addressBook!!.containsNode(id)
    }

    fun containsAll(nodes: List<KAddress>): Boolean {
        if (addressBook == null) initAddressBook()

        return addressBook!!.getFlat().containsAll(nodes)
    }

    fun getNodeById(id: BigInteger): KAddress? {
        if (addressBook == null) initAddressBook()

        return addressBook!!.getNodeById(id)
    }
}