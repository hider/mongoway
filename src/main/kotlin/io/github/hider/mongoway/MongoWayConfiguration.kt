package io.github.hider.mongoway

import com.mongodb.MongoClientSettings
import io.github.hider.mongoway.errors.StartupException
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.pojo.ClassModel
import org.bson.codecs.pojo.PojoCodecProvider
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.FileSystemResourceLoader
import org.springframework.core.io.Resource
import org.springframework.shell.core.NonInteractiveShellRunner
import org.springframework.shell.core.ShellRunner
import org.springframework.shell.core.command.*
import java.io.PrintWriter


@Configuration
class MongoWayConfiguration {

    @Bean
    fun customCodecRegistry(): CodecRegistry {
        return CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(sourceTypeMap.toCodecProvider()),
        )
    }

    @Bean
    fun mongoWayResourceLoader(): FileSystemResourceLoader {
        return object : FileSystemResourceLoader() {
            override fun getResource(location: String): Resource {
                if (location.startsWith("/")) {
                    return super.getResource("file:$location")
                }
                return super.getResource(location)
            }
        }
    }

    @Bean
    fun env() = Env(
        System.getProperty("user.name").blankToNull() ?: "??",
        System.getenv("HOSTNAME").blankToNull()
            ?: System.getenv("COMPUTERNAME").blankToNull()
            ?: Runtime.getRuntime()
                .runCatching {
                    exec(arrayOf("hostname"))
                }
                .map { process ->
                    process.inputReader().use {
                        it.readLine().ifBlank { null }
                    }
                }
                .onFailure {
                    System.err.println("Unable to determine system hostname")
                }
                .getOrNull()
    )

    @Workaround
    @Bean
    fun mongoWayCommandParser(commandRegistry: CommandRegistry): CommandParser {
        val mongoWayCommands = setOf(
            "query",
            "rollback",
            "update",
            "validate",
        )
        val commandSplitter = "\\s+(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()
        return object : DefaultCommandParser(commandRegistry) {
            override fun parse(input: String): ParsedInput {
                val words = input.split(commandSplitter)
                // https://docs.spring.io/spring-shell/reference/commands/syntax.html#_parsing_rules
                if (words.size > 1 && words[0] in mongoWayCommands) {
                    return words.joinToString(" ") {
                        if (it == "-") "\"$it\"" else it
                    }.let {
                        super.parse(it)
                    }
                }
                return super.parse(input)
            }
        }
    }

    @Workaround
    @Bean
    @ConditionalOnProperty(prefix = "spring.shell.interactive", name = ["enabled"], havingValue = "false")
    fun springShellApplicationRunner(shellRunner: ShellRunner): ApplicationRunner {
        if (shellRunner is NonInteractiveShellRunner) {
            return { args ->
                try {
                    shellRunner.run(args.sourceArgs)
                } catch (e: CommandExecutionException) {
                    if (e.exitCode == ExitStatus.USAGE_ERROR.code()) {
                        val outputWriter = NonInteractiveShellRunner::class
                            .java
                            .getDeclaredField("outputWriter")
                            .apply {
                                isAccessible = true
                            }
                            .get(shellRunner)
                        if (outputWriter is PrintWriter) {
                            outputWriter.flush()
                        }
                        val command = "Unable to execute command (\\w+) .*"
                            .toRegex()
                            .find(e.message!!)
                            ?.groupValues
                            ?.get(1)
                            ?: "<command>"
                        throw StartupException("Invalid usage. See '$command --help' for help.", e)
                    }
                    throw e
                }
            }
        }
        return { shellRunner.run(it.sourceArgs) }
    }

    data class Env(
        val username: String,
        val hostname: String?,
    )
}

private fun Map<Class<out ChangeAction>, String>.toCodecProvider(): CodecProvider {
    val sealedClassModel =
        ClassModel.builder(ChangeAction::class.java)
            .discriminatorKey("action")
            .enableDiscriminator(true)
            .build()

    val actionModels = map { (action, discriminator) ->
        ClassModel.builder(action)
            .discriminator(discriminator)
            .build()
    }
        .toTypedArray()

    return PojoCodecProvider.builder()
        .register(sealedClassModel)
        .register(*actionModels)
        .build()
}

private fun String?.blankToNull(): String? = if (isNullOrBlank()) null else this
