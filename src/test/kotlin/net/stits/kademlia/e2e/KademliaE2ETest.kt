package net.stits.kademlia.e2e

import net.stits.Application
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner

data class Ports(val web: Int, val p2p: Int) {
    fun toProperties(): Map<String, Any> {
        return mapOf(
                "server.port" to web,
                "node.port" to p2p
        )
    }
}

@RunWith(SpringJUnit4ClassRunner::class)
@SpringBootTest
class KademliaE2ETest {
    val pivotPorts = Ports(8080, 1337)
    val nodesPorts = arrayListOf(
            Ports(8081, 1338),
            Ports(8082, 1339),
            Ports(8083, 1340)
    )

    val apps = arrayListOf<SpringApplicationBuilder>()
    var appContexts: List<ConfigurableApplicationContext>? = null

    @Before
    fun initNodes() {
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
}