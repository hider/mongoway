package io.github.hider.mongoway

import org.bson.BsonInvalidOperationException
import org.bson.BsonType
import org.bson.Document
import org.bson.UuidRepresentation
import org.bson.codecs.*
import org.bson.codecs.configuration.CodecRegistries
import org.bson.json.JsonParseException
import org.bson.json.JsonReader
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResourceLoader
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component

@Component
class ActionContext(
    private val mongoWayResourceLoader: FileSystemResourceLoader,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * @see Document.DEFAULT_CODEC
     */
    private val documentCodec = CodecRegistries.withUuidRepresentation(
        CodecRegistries.fromProviders(
            ValueCodecProvider(),
            CollectionCodecProvider(),
            IterableCodecProvider(),
            BsonValueCodecProvider(),
            DocumentCodecProvider(),
            MapCodecProvider(),
        ), UuidRepresentation.STANDARD
    )
        .get(Document::class.java)

    private val decoderContext = DecoderContext.builder().build()

    fun toDocument(path: String): Document {
        val reader = toReader(path)
        return documentCodec.decode(reader, decoderContext)
    }

    fun toDocument(resource: Resource): Document {
        val reader = toReader(resource)
        return documentCodec.decode(reader, decoderContext)
    }

    fun toDocuments(path: String): List<Document> {
        try {
            val reader = toReader(path)
            return toDocuments(reader)
        } catch (e: IllegalArgumentException) {
            throw ChangeValidationException("external document [$path] ${e.message}", e)
        }
    }

    fun toDocuments(resource: Resource): List<Document> {
        try {
            val reader = toReader(resource)
            return toDocuments(reader)
        } catch (e: IllegalArgumentException) {
            throw ChangeValidationException("external document [$resource] ${e.message}", e)
        }
    }

    private fun toResource(path: String): Resource {
        return mongoWayResourceLoader.getResource(path)
    }

    private fun toDocuments(reader: JsonReader): List<Document> {
        val result = mutableListOf<Document>()
        try {
            reader.readStartArray()
        } catch (e: JsonParseException) {
            throw IllegalArgumentException("should be a JSON array", e)
        } catch (e: BsonInvalidOperationException) {
            throw IllegalArgumentException("should be a JSON array", e)
        }
        while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            val doc = documentCodec.decode(reader, decoderContext)
            result.add(doc)
        }
        reader.readEndArray()
        return result
    }

    private fun toReader(resource: Resource): JsonReader {
        if (!resource.isReadable) {
            throw IllegalArgumentException("is not readable. Ensure the resource exists")
        }
        if (resource.isFile) {
            log.info("Reading external file ${resource.file.absolutePath}")
        } else {
            log.info("Reading external resource: ${resource.description}")
        }
        return JsonReader(resource.inputStream.bufferedReader())
    }

    private fun toReader(path: String): JsonReader {
        val resource = toResource(path)
        return toReader(resource)
    }
}
