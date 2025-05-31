package io.github.hider.mongoway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.shell.command.annotation.CommandScan

@SpringBootApplication(
    exclude = [
        MongoAutoConfiguration::class,
        MongoRepositoriesAutoConfiguration::class,
    ],
)
@CommandScan
class MongoWayApplication

fun main(args: Array<String>) {
    runApplication<MongoWayApplication>(*args)
}
