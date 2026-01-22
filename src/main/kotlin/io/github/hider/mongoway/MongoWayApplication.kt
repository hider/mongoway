package io.github.hider.mongoway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(
    exclude = [
        MongoAutoConfiguration::class,
        DataMongoRepositoriesAutoConfiguration::class,
    ],
)
class MongoWayApplication

fun main(args: Array<String>) {
    runApplication<MongoWayApplication>(*args.ifEmpty { arrayOf("help") })
}
