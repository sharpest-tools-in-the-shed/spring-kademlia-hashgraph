package net.stits.kademlia.services

import net.stits.ID_SPACE_SIZE
import net.stits.K_PARAMETER
import net.stits.kademlia.data.KAddress
import net.stits.kademlia.node.KAddressBook
import net.stits.kademlia.node.KHeap
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigInteger
import javax.annotation.PostConstruct


@Service
class DiscoveryService {
    @Autowired
    lateinit var identityService: IdentityService

    lateinit var addressBook: KAddressBook

    @PostConstruct
    fun init() {
        addressBook = KHeap(identityService.getKAddress(), K_PARAMETER, ID_SPACE_SIZE)
    }

    fun addNode(address: KAddress): Boolean {
        return addressBook.addNode(address)
    }

    fun addNodes(addresses: List<KAddress>) {
        return addressBook.addNodes(addresses)
    }

    fun toList(): List<KAddress> {
        return addressBook.getFlat().filter { it.getId() != addressBook.me.getId() }
    }

    fun addNewNodes(nodes: List<KAddress>): List<KAddress> {
        val notAskedNodes = nodes.filter { !containsId(it.getId()) }
        addNodes(nodes)
        return notAskedNodes
    }

    fun removeNode(address: KAddress): Boolean {
        return addressBook.removeNode(address)
    }

    fun getClosest(id: BigInteger): KAddress? {
        return addressBook.getClosestTo(id).firstOrNull()
    }

    fun getClosestKBucket(id: BigInteger): List<KAddress> {
        return addressBook.getClosestTo(id)
    }

    fun containsId(id: BigInteger): Boolean {
        return addressBook.containsNode(id)
    }

    fun containsAll(nodes: List<KAddress>): Boolean {
        return addressBook.getFlat().containsAll(nodes)
    }

    fun getNodeById(id: BigInteger): KAddress? {
        return addressBook.getNodeById(id)
    }
}