package io.github.hider.mongoway

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.validation.annotation.Validated
import org.testcontainers.containers.MongoDBContainer


@TestConfiguration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "mongoway.test")
@Validated
class Config {
    @NotBlank
    lateinit var databaseName: String

    @Bean
    @ServiceConnection
    fun testMongo(): MongoDBContainer {
        return MongoDBContainer("mongo:8.0.11")
    }
}
