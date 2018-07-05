package net.stits

import net.stits.osen.P2P
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment


val K_PARAMETER = 2
val ID_SPACE_SIZE = 256

@SpringBootApplication
open class Application {
    private val packageToScan = Application::class.java.`package`.name

    @Bean
    open fun p2pInitializer(env: Environment): P2P {
        return P2P(packageToScan = packageToScan)
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}
