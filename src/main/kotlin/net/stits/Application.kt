package net.stits

import net.stits.osen.P2P
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean


val K_PARAMETER = 2
val ID_SPACE_SIZE = 256

@SpringBootApplication
open class Application {
    private val packageToScan = Application::class.java.`package`.name

    @Bean
    open fun p2pInitializer(): P2P {
        return P2P(basePackages = arrayOf(packageToScan), maxPacketSizeBytes = 10 * 1024)
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}
