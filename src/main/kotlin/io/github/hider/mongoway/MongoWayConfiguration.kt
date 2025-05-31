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
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
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
}

private class PatchedDataClassCodecProvider : CodecProvider {
    override fun <T : Any> get(clazz: Class<T>, registry: CodecRegistry): Codec<T>? = get(clazz, emptyList(), registry)

    override fun <T : Any> get(clazz: Class<T>, typeArguments: List<Type>, registry: CodecRegistry): Codec<T>? =
        PatchedDataClassCodec.create(clazz.kotlin, registry, typeArguments)
}
