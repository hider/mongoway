package io.github.hider.mongoway.commands

import org.springframework.stereotype.Component

/**
 * IntelliJ IDEA workaround to detect `@Command` classes as Spring Beans (for inspection)
 * without double Bean registration by Spring Boot.
 */
@Retention(AnnotationRetention.SOURCE)
@Component
internal annotation class IdeaCommandDetection
