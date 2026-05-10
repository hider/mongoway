package io.github.hider.mongoway.aot

import com.sun.el.ExpressionFactoryImpl
import io.github.hider.mongoway.DatabaseChangelogRepository
import io.github.hider.mongoway.MongoWayConfiguration
import org.hibernate.validator.internal.util.logging.`Log_$logger`
import org.hibernate.validator.internal.util.logging.`Messages_$bundle`
import org.springframework.aop.framework.Advised
import org.springframework.aot.hint.*
import org.springframework.core.DecoratingProxy
import org.springframework.data.repository.Repository
import org.springframework.transaction.interceptor.TransactionalProxy
import kotlin.reflect.jvm.javaMethod

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
            // Fixes: java.lang.IllegalArgumentException: Invalid logger interface org.hibernate.validator.internal.util.logging.Log (implementation not found)
            // Fixes: org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively invoke constructor 'public org.hibernate.validator.internal.util.logging.Log_$logger(org.jboss.logging.Logger)'
            .registerType<`Log_$logger`>(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
            // Fixes: java.lang.IllegalArgumentException: Invalid bundle interface org.hibernate.validator.internal.util.logging.Messages (implementation not found)
            // Fixes: org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively read or write field 'public static final org.hibernate.validator.internal.util.logging.Messages_$bundle org.hibernate.validator.internal.util.logging.Messages_$bundle.INSTANCE'
            .registerType<`Messages_$bundle`>(MemberCategory.ACCESS_PUBLIC_FIELDS)
            // Fixes: jakarta.validation.ValidationException: HV000183: Unable to initialize 'jakarta.el.ExpressionFactory'. Check that you have the EL dependencies on the classpath, or use ParameterMessageInterpolator instead, java.lang.ClassNotFoundException: org.osgi.framework.FrameworkUtil
            .registerType<ExpressionFactoryImpl>(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
            // Fixes: Hibernate Validator cannot instantiate AggregateResourceBundle.CONTROL. This can happen most notably in a Google App Engine environment or when running Hibernate Validator as Java 9 named module. A PlatformResourceBundleLocator without bundle aggregation was created. This only affects you in case you are using multiple ConstraintDefinitionContributor JARs. ConstraintDefinitionContributors are a Hibernate Validator specific feature. All Jakarta Validation features work as expected. See also https://hibernate.atlassian.net/browse/HV-1023.
            .registerMethod(Module::isNamed.javaMethod!!, ExecutableMode.INVOKE)
        hints.resources()
            .registerPattern("org/hibernate/validator/ValidationMessages.properties")
            // Logback starts earlier than Spring Boot on native run
            .registerPattern("logback.xml")
            .registerPattern("org/springframework/boot/logging/logback/defaults.xml")
            .registerPattern("org/springframework/boot/logging/logback/console-appender.xml")
    }
}
