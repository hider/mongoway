package io.github.hider.mongoway.aot

import io.github.hider.mongoway.DatabaseChangelogRepository
import io.github.hider.mongoway.MongoWayConfiguration
import org.springframework.aop.framework.Advised
import org.springframework.aot.hint.BindingReflectionHintsRegistrar
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.registerJdkProxy
import org.springframework.core.DecoratingProxy
import org.springframework.data.repository.Repository
import org.springframework.transaction.interceptor.TransactionalProxy

/**
 * Registers missing runtime classes for AOT processing.
 *
 * @see org.springframework.aot.hint.annotation.RegisterReflectionForBindingProcessor
 */
class AotRuntimeHints : RuntimeHintsRegistrar {
    private val bindingRegistrar = BindingReflectionHintsRegistrar()

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.proxies().registerJdkProxy(
            DatabaseChangelogRepository::class,
            Repository::class,
            TransactionalProxy::class,
            Advised::class,
            DecoratingProxy::class,
        )
        bindingRegistrar.registerReflectionHints(hints.reflection(), *MongoWayConfiguration().sourceTypeMap().keys.toTypedArray())
        hints.reflection()
            // Fixes: kotlin.reflect.jvm.internal.KotlinReflectionInternalError: Unresolved class: class java.util.Collections$SingletonMap (kind = null)
            .registerType(Class.forName($$"java.util.Collections$SingletonMap"))
    }
}
