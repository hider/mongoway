package io.github.hider.mongoway.aot

import io.github.hider.mongoway.DatabaseChangelogRepository
import org.springframework.aop.framework.Advised
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.registerJdkProxy
import org.springframework.core.DecoratingProxy
import org.springframework.data.repository.Repository
import org.springframework.transaction.interceptor.TransactionalProxy

class AotRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.proxies().registerJdkProxy(
            DatabaseChangelogRepository::class,
            Repository::class,
            TransactionalProxy::class,
            Advised::class,
            DecoratingProxy::class,
        )
    }
}
