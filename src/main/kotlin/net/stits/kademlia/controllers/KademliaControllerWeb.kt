package net.stits.kademlia.controllers

import com.fasterxml.jackson.annotation.JsonProperty
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


data class BootstrapReq(
        @JsonProperty("host") val host: String,
        @JsonProperty("port") val port: Int
)

data class StoreReq(
        @JsonProperty("value") val value: String
)

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


    @GetMapping("/address/me")
    fun getMyAddress(): KAddress {
        return identityService.getKAddress()
    }

    @GetMapping("/address/list")
    fun getAddressBook(): List<KAddress> {
        return discoveryService.toList()
    }

    @GetMapping("/ping/{id}")
    fun ping(@PathVariable id: String): String {
        val _id = BigInteger(id)

        return kademliaService.ping(_id)
    }

    @PostMapping("/bootstrap")
    fun bootstrap(@RequestBody input: BootstrapReq): String {
        kademliaService.bootstrap(Address(input.host, input.port), identityService.getId())
        return "Ok"
    }

    @GetMapping("/storage/list")
    fun getStorage(): Map<String, Any> {
        val storage = storageService.getStorage().mapKeys { it.key.toString(16) }

        return storage
    }

    @PostMapping("/storage/put")
    fun storeString(@RequestBody input: StoreReq): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(input.value.toByteArray(StandardCharsets.UTF_8))
        val digest = md.digest()
        val id = BigInteger(digest)

        storageService.put(id, input.value)
        val storedRemotely = kademliaService.store(id, input.value)

        if (!storedRemotely)
            throw RuntimeException("Unable to store value: ${input.value} id: $id (duplicate id)")

        return id.toString(10)
    }

    @GetMapping("/storage/{id}")
    fun getFromStorage(@PathVariable id: String): Any? {
        val _id = BigInteger(id)

        return storageService.get(_id) ?: kademliaService.findValue(_id)
    }
}
