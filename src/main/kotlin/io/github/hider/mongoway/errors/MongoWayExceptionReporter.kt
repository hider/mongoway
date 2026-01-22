package io.github.hider.mongoway.errors

import org.springframework.boot.Banner.Mode
import org.springframework.boot.SpringBootExceptionReporter
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.getProperty

class MongoWayExceptionReporter(context: ConfigurableApplicationContext) : SpringBootExceptionReporter {
    private val debugMode = context.environment.getProperty("spring.shell.debug.enabled", false)
    private val quietMode = context.environment.getProperty<Mode>("spring.main.banner-mode") == Mode.OFF

    override fun reportException(failure: Throwable): Boolean {
        val cause = generateSequence(failure) { it.cause }
            .find { it is MongoWayException }
        val hasReported = !debugMode && quietMode && cause != null
        if (hasReported) {
            System.err.println("[30;41;1m Error [0m ${cause.message}")
        }
        return hasReported
    }
}
