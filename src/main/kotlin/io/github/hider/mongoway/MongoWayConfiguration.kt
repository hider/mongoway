package io.github.hider.mongoway

import com.mongodb.MongoClientSettings
import org.bson.codecs.Codec
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.kotlin.PatchedDataClassCodec
import org.bson.codecs.pojo.ClassModel
import org.bson.codecs.pojo.PojoCodecProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.FileSystemResourceLoader
import org.springframework.core.io.Resource
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.shell.core.command.CommandParser
import org.springframework.shell.core.command.CommandRegistry
import org.springframework.shell.core.command.DefaultCommandParser
import org.springframework.shell.core.command.ParsedInput
import java.lang.reflect.Type


@Configuration
class MongoWayConfiguration {

    @Bean
    fun sourceTypeMap(): Map<Class<out ChangeAction>, String> {
        return ChangeAction::class.sealedSubclasses
            .flatMap { it.sealedSubclasses + it }
            .filter { it.isData }
            .map { it.java }
            .associateWith { action -> action.simpleName.replaceFirstChar { it.lowercase() } }
    }

    @Bean
    fun customCodecRegistry(sourceTypeMap: Map<Class<out ChangeAction>, String>): CodecRegistry {
        return CodecRegistries.fromRegistries(
            CodecRegistries.fromProviders(PatchedDataClassCodecProvider()),
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(createChangeActionCodecProvider(sourceTypeMap)),
        )
    }

    @Bean
    fun customConversions(): MongoCustomConversions {
        return MongoCustomConversions(emptyList<Any>())
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

    private fun createChangeActionCodecProvider(sourceTypeMap: Map<Class<out ChangeAction>, String>): CodecProvider {
        val sealedClassModel =
            ClassModel.builder(ChangeAction::class.java)
                .discriminatorKey("action")
                .enableDiscriminator(true)
                .build()

        val actionModels = sourceTypeMap.map { (action, discriminator) ->
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

    data class Env(
        val username: String,
        val hostname: String?,
    )

    @Workaround
    private class PatchedDataClassCodecProvider : CodecProvider {
        override fun <T : Any> get(clazz: Class<T>, registry: CodecRegistry): Codec<T>? = get(clazz, emptyList(), registry)

        override fun <T : Any> get(clazz: Class<T>, typeArguments: List<Type>, registry: CodecRegistry): Codec<T>? =
            PatchedDataClassCodec.create(clazz.kotlin, registry, typeArguments)
    }

    private fun String?.blankToNull(): String? = if (isNullOrBlank()) null else this
}
