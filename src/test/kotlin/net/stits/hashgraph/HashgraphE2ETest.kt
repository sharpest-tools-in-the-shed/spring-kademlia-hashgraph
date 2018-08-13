package net.stits.hashgraph

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import net.stits.Application
import net.stits.kademlia.KademliaNodeClient
import net.stits.kademlia.e2e.Ports
import net.stits.kademlia.e2e.createNodes
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner


@RunWith(SpringJUnit4ClassRunner::class)
class HashgraphE2ETest {
    val restTemplateBuilder = RestTemplateBuilder()

    lateinit var pivotPorts: Ports
    lateinit var nodesPorts: List<Ports>
    val host = "localhost"

    val nodesCount = 4 // change this to change number of nodes

    lateinit var pivotClient: KademliaNodeClient
    lateinit var nodeClients: List<KademliaNodeClient>

    val apps = arrayListOf<SpringApplicationBuilder>()
    var appContexts: List<ConfigurableApplicationContext>? = null

    @Before
    fun initNodes() {
        val (p, n) = createNodes(nodesCount)
        pivotPorts = p
        nodesPorts = n

        val pivotRestTemplate = TestRestTemplate(restTemplateBuilder)
        val nodesRestTemplates = nodesPorts.map { TestRestTemplate(restTemplateBuilder) }

        nodeClients = nodesRestTemplates.mapIndexed { index: Int, testRestTemplate: TestRestTemplate ->
            KademliaNodeClient(testRestTemplate, "$host:${nodesPorts[index].web}")
        }
        pivotClient = KademliaNodeClient(pivotRestTemplate, "$host:${pivotPorts.web}")

        apps.add(SpringApplicationBuilder(Application::class.java).properties(pivotPorts.toProperties()))
        nodesPorts.forEach { ports ->
            apps.add(SpringApplicationBuilder(Application::class.java).properties(ports.toProperties()))
        }

        appContexts = apps.map { it.run() }
    }

    @Test
    fun processTest() {
        assert(
                nodeClients
                        .map { it.bootstrap(host, pivotPorts.p2p) }
                        .all { resp -> resp == "Ok" }
        ) { "Unable to bootstrap all nodes" }

        assert(
                nodeClients
                        .map { it.startSyncing() }
                        .all { resp -> resp == "Ok" }
        ) { "Unable to start syncing on all nodes" }

        runBlocking {
            delay(10000)

            val eventsByClients = nodeClients
                    .map { it.getEvents() }

            println(eventsByClients)
        }
    }

    @After
    fun destroyNodes() {
        appContexts!!.forEach { it.close() }
    }
}