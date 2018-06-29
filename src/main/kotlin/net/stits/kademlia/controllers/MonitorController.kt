package net.stits.kademlia.controllers

import net.stits.kademlia.data.KAddress
import net.stits.kademlia.services.DiscoveryService
import net.stits.kademlia.services.IdentityService
import net.stits.kademlia.services.KademliaService
import net.stits.kademlia.services.StorageService
import net.stits.osen.Address
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest


@RestController
class KademliaMonitorController {
    @Autowired
    lateinit var identityService: IdentityService

    @Autowired
    lateinit var discoveryService: DiscoveryService

    @Autowired
    lateinit var storageService: StorageService

    @Autowired
    lateinit var kademliaService: KademliaService

    @GetMapping("/addressbook")
    fun getAddressBook(): List<KAddress> {
        return discoveryService.toList()
    }

    @GetMapping("/storage")
    fun getStorage(): Map<BigInteger, ByteArray> {
        return storageService.getStorage()
    }

    @PostMapping("/bootstrap")
    fun bootstrap(@RequestParam host: String, @RequestParam port: Int): String {
        kademliaService.bootstrap(Address(host, port), identityService.getId())
        return "Ok"
    }

    @PostMapping("/store")
    fun store(@RequestParam value: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(value.toByteArray(StandardCharsets.UTF_8))
        val digest = md.digest()
        val id = BigInteger(digest)

        kademliaService.store(id, value)

        return "Ok"
    }
}
