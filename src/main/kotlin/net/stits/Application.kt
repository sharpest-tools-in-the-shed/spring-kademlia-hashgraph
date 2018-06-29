package net.stits

import net.stits.osen.P2P
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.PropertySource


val K_PARAMETER = 2
val ID_SPACE_SIZE = 256

@SpringBootApplication
@PropertySource("classpath:app.properties")
open class Application {
    private val packageToScan = Application::class.java.`package`.name

    @Value("\${node.port}")
    private var port: Int = 1337

    @Bean
    open fun p2pInitializer(): P2P {
        return P2P(listeningPort = port, packageToScan = packageToScan)
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}
