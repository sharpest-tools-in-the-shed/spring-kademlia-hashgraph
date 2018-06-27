package net.stits.kademlia.services

import net.stits.ID_SPACE_SIZE
import net.stits.K_PARAMETER
import net.stits.MY_ADDRESS
import net.stits.kademlia.data.KAddress
import net.stits.kademlia.node.KAddressBook
import net.stits.kademlia.node.KHeap
import org.springframework.stereotype.Service
import java.math.BigInteger


@Service
class DiscoveryService {
    private val addressBook: KAddressBook = KHeap(MY_ADDRESS, K_PARAMETER, ID_SPACE_SIZE)

    fun addNode(address: KAddress): Boolean {
        return addressBook.addNode(address)
    }

    fun removeNode(address: KAddress): Boolean {
        return addressBook.removeNode(address)
    }

    fun getClosestKBucket(id: BigInteger): List<KAddress> {
        return addressBook.getClosestTo(id)
    }
}