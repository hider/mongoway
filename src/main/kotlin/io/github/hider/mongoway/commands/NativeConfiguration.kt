package io.github.hider.mongoway.commands

import io.github.hider.mongoway.Workaround
import jakarta.annotation.PostConstruct
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.core.NativeDetector
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.annotation.Command
import org.springframework.shell.core.command.annotation.support.CommandFactoryBean
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.javaMethod

@Workaround
@Configuration
class NativeConfiguration(
    private val commandRegistry: CommandRegistry,
    private val applicationContext: ApplicationContext,
) {
    /**
     * @see org.springframework.shell.core.autoconfigure.CommandRegistryAutoConfiguration.registerAnnotatedCommands
     */
    @PostConstruct
    fun registerCommands() {
        if (NativeDetector.inNativeImage(NativeDetector.Context.RUN)) {
            sequenceOf(
                QueryCommand::class,
                RollbackCommand::class,
                UpdateCommand::class,
                ValidateCommand::class,
            )
                .flatMap { it.declaredMemberFunctions }
                .filter { it.hasAnnotation<Command>() }
                .mapNotNull { it.javaMethod }
                .forEach { method ->
                    val factoryBean = CommandFactoryBean(method)
                    factoryBean.setApplicationContext(applicationContext)
                    commandRegistry.registerCommand(factoryBean.getObject())
                }
        }
    }
}
