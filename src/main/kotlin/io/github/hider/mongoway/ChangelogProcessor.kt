package io.github.hider.mongoway

import io.github.hider.mongoway.errors.ChangeValidationException
import io.github.hider.mongoway.errors.changelogError
import io.github.hider.mongoway.errors.handleParseError
import org.bson.BsonInvalidOperationException
import org.bson.BsonType
import org.bson.codecs.DecoderContext
import org.bson.codecs.configuration.CodecConfigurationException
import org.bson.codecs.configuration.CodecRegistry
import org.bson.json.JsonParseException
import org.bson.json.JsonReader
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResourceLoader
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.io.Reader

@Component
class ChangelogProcessor(
    private val mongoWayResourceLoader: FileSystemResourceLoader,
    private val customCodecRegistry: CodecRegistry,
    private val actionContext: ActionContext,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun process(paths: Array<out String>, failFast: Boolean): Sequence<ProcessedChangeSet> {
        return if (paths.size == 1 && paths.first() == "-") {
            fromStdinToSequence()
        } else {
            paths.asResourceSequence()
        }
            .onEach {
                if (failFast) it.result.onFailure { ex ->
                    throw ChangeValidationException(
                        changelogError(it.path, ex.message),
                        ex,
                    )
                }
            }
    }

    fun preValidate(changeSet: ChangeSet, changelogPath: Resource): Hash {
        if (changeSet.targetCollection == CHANGELOG_COLLECTION_NAME) {
            throw ChangeValidationException("changeSet[globalUniqueChangeId=${changeSet.globalUniqueChangeId}].targetCollection must not be '${changeSet.targetCollection}'.")
        }
        return try {
            changeSet.change.validateOrThrow(actionContext, changelogPath)
        } catch (e: ChangeValidationException) {
            throw ChangeValidationException(
                "changeSet[globalUniqueChangeId=${changeSet.globalUniqueChangeId}] validation error: ${e.message}.",
                e
            )
        }
    }

    private fun fromStdinToSequence() = sequence {
        log.info("Reading change log from standard input...")
        val path = System.getProperty("user.dir")
        System.`in`.bufferedReader()
            .use { reader ->
                yieldAll(reader.readChanges().map { ProcessedChangeSet(path, it) })
            }
    }

    private fun Array<out String>.asResourceSequence() = sequence {
        for ((idx, path) in this@asResourceSequence.withIndex()) {
            if (path.isBlank()) {
                yield(
                    ProcessedChangeSet(
                        path,
                        Result.failure(ChangeValidationException("Path must not be blank at index ${idx + 1}."))
                    )
                )
                continue
            }
            val changelog = mongoWayResourceLoader.getResource(path)
            if (!changelog.isReadable) {
                yield(
                    ProcessedChangeSet(
                        path,
                        Result.failure(ChangeValidationException("$changelog is not readable. Ensure the resource exists."))
                    )
                )
                continue
            }
            if (changelog.isFile) {
                log.info("Processing change log file ${changelog.file.absolutePath}")
            } else {
                log.info("Processing change log resource: ${changelog.description}")
            }
            changelog.inputStream
                .bufferedReader()
                .use { reader ->
                    yieldAll(reader.readChanges().map { ProcessedChangeSet(path, it) })
                }
        }
    }

    private fun Reader.readChanges() = sequence {
        val reader = JsonReader(this@readChanges)
        val decoderContext = DecoderContext.builder().build()
        var parseException: ChangeValidationException? = null
        try {
            reader.readStartArray()
        } catch (e: JsonParseException) {
            parseException = ChangeValidationException("Change log should be a JSON array.", e)
        } catch (e: BsonInvalidOperationException) {
            parseException = ChangeValidationException("Change log should be a JSON array.", e)
        }
        if (parseException != null) {
            yield(Result.failure(parseException))
            return@sequence
        }
        var counter = 0
        while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            val changeSet = runCatching {
                try {
                    customCodecRegistry.get(ChangeSet::class.java).decode(reader, decoderContext)
                } catch (e: CodecConfigurationException) {
                    throw handleParseError(e, counter)
                }
            }
            yield(changeSet)
            counter += 1
        }
        reader.readEndArray()
    }

    data class ProcessedChangeSet(
        val path: String,
        val result: Result<ChangeSet>,
    )
}
