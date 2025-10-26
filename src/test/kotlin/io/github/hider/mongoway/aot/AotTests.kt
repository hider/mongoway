package io.github.hider.mongoway.aot

import io.github.hider.mongoway.ChangeAction
import io.github.hider.mongoway.DatabaseChangelogRepository
import org.assertj.core.api.Assertions.assertThat
import org.springframework.aop.framework.Advised
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.core.DecoratingProxy
import org.springframework.data.repository.Repository
import org.springframework.transaction.interceptor.TransactionalProxy
import kotlin.test.Test


class AotTests {

    @Test
    fun `aot resources available`() {
        val hints = RuntimeHints()
        AotRuntimeHints().registerHints(hints, javaClass.classLoader)
        assertThat(
            RuntimeHintsPredicates.proxies().forInterfaces(
                DatabaseChangelogRepository::class.java,
                Repository::class.java,
                TransactionalProxy::class.java,
                Advised::class.java,
                DecoratingProxy::class.java,
            )
        ).accepts(hints)

        val actions = ChangeAction::class.sealedSubclasses
            .flatMap { it.sealedSubclasses + it }
            .filter { it.isData }
            .map { it.java }
        assertThat(
            hints.reflection()
                .typeHints()
                .filter { it.type.packageName == "io.github.hider.mongoway" }
                .map {
                    Class.forName(it.type.canonicalName)
                }
        ).containsAll(actions)
    }
}
