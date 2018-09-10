package net.stits.hashgraph

import net.stits.hashgraph.services.ConsensusService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController


@RestController
class HashgraphControllerWeb {
    @Autowired
    lateinit var consensusService: ConsensusService

    @GetMapping("/events/consensus")
    fun getEvents() = consensusService.getConsensusEvents()

    @PostMapping("/sync/start")
    fun startSyncing(): String {
        consensusService.startSyncing()
        return "Ok"
    }
}