package net.stits.hashgraph

import net.stits.hashgraph.services.ConsensusService
import net.stits.hashgraph.services.EventInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigInteger


@RestController
class HashgraphControllerWeb {
    @Autowired
    lateinit var consensusService: ConsensusService

    @GetMapping("/events/{offset}")
    fun getEvents(@RequestParam offset: String): List<EventInfo> {
        val _offset = BigInteger(offset)

        return if (_offset == BigInteger.ZERO) consensusService.getEventsInfo()
        else consensusService.getEventsInfo(_offset)
    }

    @PostMapping("/sync/start")
    fun startSyncing(): String {
        consensusService.startSyncing()
        return "Ok"
    }
}