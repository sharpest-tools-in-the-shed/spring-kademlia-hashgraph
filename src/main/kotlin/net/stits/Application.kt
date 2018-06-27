package net.stits

import net.stits.kademlia.data.KAddress
import net.stits.osen.Address
import net.stits.osen.P2P
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import java.math.BigInteger


val MY_ADDRESS = KAddress(Address("localhost", 1337), BigInteger.ONE)
val K_PARAMETER = 2
val ID_SPACE_SIZE = 256

@SpringBootApplication
open class Application {
    private val packageToScan = "net.stits"

    @Bean
    open fun p2pInitializer(): P2P {
        return P2P(listeningPort = MY_ADDRESS.getPort(), packageToScan = packageToScan)
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}
