package io.github.hider.mongoway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.core.NativeDetector
import org.springframework.core.NativeDetector.Context

@SpringBootApplication(
    exclude = [
        MongoAutoConfiguration::class,
        DataMongoRepositoriesAutoConfiguration::class,
    ],
)
class MongoWayApplication

fun main(args: Array<String>) {
    if (NativeDetector.inNativeImage(Context.RUN)) {
        System.setProperty("user.name", System.getenv("USER") ?: System.getenv("USERNAME") ?: "??")
    }
    runApplication<MongoWayApplication>(*args.ifEmpty { arrayOf("help") })
}
