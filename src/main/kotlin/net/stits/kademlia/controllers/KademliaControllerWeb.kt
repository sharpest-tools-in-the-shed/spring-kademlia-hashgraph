package net.stits.kademlia.controllers

import net.stits.kademlia.data.KAddress
import net.stits.kademlia.services.DiscoveryService
import net.stits.kademlia.services.IdentityService
import net.stits.kademlia.services.KademliaService
import net.stits.kademlia.services.StorageService
import net.stits.osen.Address
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest


@RestController
class KademliaControllerWeb {
    @Autowired
    lateinit var identityService: IdentityService

    @Autowired
    lateinit var discoveryService: DiscoveryService

    @Autowired
    lateinit var storageService: StorageService

    @Autowired
    lateinit var kademliaService: KademliaService


    @GetMapping("/addresses/list")
    fun getAddressBook(): List<KAddress> {
        return discoveryService.toList()
    }

    @GetMapping("/ping/{id}")
    fun ping(@PathVariable id: String): Boolean {
        val _id = BigInteger(id)

        return kademliaService.ping(_id)
    }

    @PostMapping("/bootstrap")
    fun bootstrap(@RequestParam host: String, @RequestParam port: Int): String {
        kademliaService.bootstrap(Address(host, port), identityService.getId())
        return "Ok"
    }

    @GetMapping("/storage/list")
    fun getStorage(): Map<BigInteger, Any> {
        return storageService.getStorage()
    }

    @PostMapping("/storage/add")
    fun storeString(@RequestParam value: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(value.toByteArray(StandardCharsets.UTF_8))
        val digest = md.digest()
        val id = BigInteger(digest)

        kademliaService.store(id, value)

        return "Ok"
    }

    @GetMapping("/storage/{id}")
    fun getFromStorage(@RequestParam id: String): Any? {
        val _id = BigInteger(id)
        return storageService.get(_id)
    }
}
