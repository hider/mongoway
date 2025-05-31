package io.github.hider.mongoway

import org.springframework.boot.Banner.Mode
import org.springframework.boot.SpringBootExceptionReporter
import org.springframework.context.ConfigurableApplicationContext

class MongoWayExceptionReporter(private val context: ConfigurableApplicationContext) : SpringBootExceptionReporter {
    override fun reportException(failure: Throwable): Boolean {
        val silentMode = context.environment
            .getProperty("spring.main.banner-mode", Mode::class.java) == Mode.OFF
        return silentMode && failure is ChangeValidationException
    }
}
