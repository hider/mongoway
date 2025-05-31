package io.github.hider.mongoway.commands

import io.github.hider.mongoway.MongoConnection
import io.github.hider.mongoway.DatabaseChangelog
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.json.JsonMode
import org.bson.json.JsonWriter
import org.bson.json.JsonWriterSettings
import org.slf4j.LoggerFactory
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option
import java.io.StringWriter


@IdeaCommandDetection
@Command(group = COMMAND_GROUP)
class QueryCommand(
    private val connection: MongoConnection,
    private val customCodecRegistry: CodecRegistry,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Command(description = "Query a change set by globalUniqueChangeId.")
    fun query(
        @Option(required = true, description = CS_DESCRIPTION) connectionString: String,
        @Option(required = true) globalUniqueChangeId: String,
    ) {
        connection.useDatabase(connectionString) { _, databaseChangelog ->
            val changelog =
                databaseChangelog.findByChangeSetGlobalUniqueChangeIdAndRollbackRolledBackChangelogIdIsNullAndRollbackChangelogIdIsNullOrderByIdDesc(
                    globalUniqueChangeId
                )
            if (changelog == null) {
                log.info("Change set with globalUniqueChangeId '{}' not found.", globalUniqueChangeId)
            } else {
                val writer = JsonWriter(
                    StringWriter(),
                    JsonWriterSettings.builder().outputMode(JsonMode.SHELL).indent(true).build()
                )
                customCodecRegistry.get(DatabaseChangelog::class.java)
                    .encode(
                        writer,
                        changelog,
                        EncoderContext.builder().build()
                    )
                println(writer.writer.toString())
            }
        }
    }
}
