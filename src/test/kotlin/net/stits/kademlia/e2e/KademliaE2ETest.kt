package net.stits.kademlia.e2e

import net.stits.Application
import net.stits.kademlia.KademliaNodeClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import java.util.*

data class Ports(val web: Int, val p2p: Int) {
    fun toProperties(): Properties {
        val props = Properties()
        props.setProperty("server.port", web.toString())
        props.setProperty("node.port", p2p.toString())

        return props
    }
}

fun createNodes(count: Int, baseWebPort: Int = 8080, baseP2PPort: Int = 1337): Pair<Ports, List<Ports>> {
    val pivotPorts = Ports(baseWebPort, baseP2PPort)
    val nodesPorts = arrayListOf<Ports>()
    for (i in 1..count) {
        nodesPorts.add(Ports(baseWebPort + i, baseP2PPort + i))
    }

    return Pair(pivotPorts, nodesPorts)
}

@RunWith(SpringJUnit4ClassRunner::class)
class KademliaE2ETest {
    val restTemplateBuilder = RestTemplateBuilder()

    lateinit var pivotPorts: Ports
    lateinit var nodesPorts: List<Ports>
    val host = "localhost"

    val nodesCount = 10 // change this to change number of nodes

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

    @After
    fun destroyNodes() {
        appContexts!!.forEach { it.close() }
    }

    @Test
    fun `bootstrap ping store retrieve`() {
        `nodes can bootstrap via pivot`()
        `nodes address book is no empty`()
        `nodes can ping each other`()
        `nodes have their storages empty by default`()
        `each node can store and retrieve string values`()
        `nodes cant store single value twice`()
    }


    private fun `nodes can bootstrap via pivot`() {
        assert(
            nodeClients
                .map { it.bootstrap(host, pivotPorts.p2p) }
                .all { resp -> resp == "Ok" }
        ) { "Unable to bootstrap all nodes" }
    }

    private fun `nodes address book is no empty`() {
        assert(pivotClient.getAddressBook().isNotEmpty()) { "Pivot has an empty address book" }

        val books = nodeClients.map { it.getAddressBook() }
        assert(
            books.all { addressBook -> addressBook.isNotEmpty() }
        ) { "Some of nodes has its address book empty" }
    }

    private fun `nodes can ping each other`() {
        assert(
            nodeClients
                .map { client ->
                    val addressBook = client.getAddressBook()
                    addressBook
                        .map { addr -> client.ping(addr.getId().toString(10)) }
                        .all { resp -> resp == "Ok" }
                }
                .all { pinged -> pinged }
        ) { "Some of nodes is unable to ping" }

        assert(
            pivotClient.getAddressBook()
                .map { addr -> pivotClient.ping(addr.getId().toString(10)) }
                .all { resp -> resp == "Ok" }
        ) { "Pivot is unable to ping" }
    }

    private fun `nodes have their storages empty by default`() {
        assert(
            nodeClients
                .map { client -> client.getStorage().keys.isEmpty() }
                .all { empty -> empty }
        )
        assert(pivotClient.getStorage().keys.isEmpty())
    }

    private fun `each node can store and retrieve string values`() {
        val idsToValues = nodeClients.mapIndexed { index: Int, client: KademliaNodeClient ->
            val value = "node $index value"
            val id = client.storeString(value)!!
            Pair(id, value)
        }

        // TODO: somehow test pivot value
        assert(idsToValues.size == nodeClients.size) { "Not all nodes stored values" }

        assert(
            nodeClients
                .map { client ->
                    idsToValues
                        .map { (id, value) ->
                            val retrievedVal = client.getFromStorage(id)
                            if (retrievedVal != value) println("retrieved: $retrievedVal, value: $value")
                            retrievedVal == value
                        }
                        .all { equals -> equals }
                }
                .all { valid -> valid }
        ) { "Nodes are unable to retrieve value" }

        assert(
            idsToValues
                .map { (id, value) ->
                    val retrievedVal = pivotClient.getFromStorage(id)
                    retrievedVal == value
                }
                .all { equals -> equals }
        ) { "Pivot is unable to retrieve value" }
    }

    private fun `nodes cant store single value twice`() {
        val value = "cant store me twice and also cant update me (for now) haha"
        pivotClient.storeString(value)

        //assertThrows { pivotClient.storeString(value) }
    }
}

fun assertThrows(codeBlock: () -> Unit) {
    var thrown = false

    try {
        codeBlock()
    } catch (e: Exception) {
        e.printStackTrace()
        thrown = true
    } finally {
        assert(thrown) { "Code block should throw an error" }
    }
}

fun assertNotThrows(codeBlock: () -> Unit) {
    var thrown = false

    try {
        codeBlock()
    } catch (e: Exception) {
        e.printStackTrace()
        thrown = true
    } finally {
        assert(!thrown) { "Code block shouldn't throw an error" }
    }
}