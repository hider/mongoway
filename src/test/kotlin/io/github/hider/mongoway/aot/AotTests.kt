package io.github.hider.mongoway.aot

import io.github.hider.mongoway.DatabaseChangelogRepository
import io.github.hider.mongoway.MongoWayConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.springframework.aop.framework.Advised
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.annotation.RegisterReflection
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.core.DecoratingProxy
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.data.repository.Repository
import org.springframework.transaction.interceptor.TransactionalProxy
import kotlin.test.Test


class AotTests {

    @Test
    fun `aot resources available`() {
        val hints = RuntimeHints()
        AotRuntimeHints().registerHints(hints, javaClass.getClassLoader())
        assertThat(
            RuntimeHintsPredicates.proxies().forInterfaces(
                DatabaseChangelogRepository::class.java,
                Repository::class.java,
                TransactionalProxy::class.java,
                Advised::class.java,
                DecoratingProxy::class.java,
            )
        ).accepts(hints)

        val classes = AnnotatedElementUtils.getMergedAnnotation(AotConfiguration::class.java, RegisterReflection::class.java)!!
            .classes
            .map { it.java }
        assertThat(classes).containsAll(MongoWayConfiguration().sourceTypeMap().keys)
    }
}
