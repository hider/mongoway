package io.github.hider.mongoway.commands

import io.github.hider.mongoway.DatabaseChangelog
import io.github.hider.mongoway.MongoConnection
import jakarta.validation.constraints.NotBlank
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.json.JsonMode
import org.bson.json.JsonWriter
import org.bson.json.JsonWriterSettings
import org.slf4j.LoggerFactory
import org.springframework.shell.core.command.annotation.Argument
import org.springframework.shell.core.command.annotation.Command
import org.springframework.stereotype.Component
import java.io.StringWriter


private const val DESCRIPTION = "Execute change sets in the change log(s) against the database."

@Component
class QueryCommand(
    private val connection: MongoConnection,
    private val customCodecRegistry: CodecRegistry,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Command(
        group = COMMAND_GROUP,
        description = "Query a change set by globalUniqueChangeId.",
        help = """$DESCRIPTION
Usage: query <connectionString> <globalUniqueChangeId>
$CS_DESCRIPTION
  [1mglobalUniqueChangeId[0m the unique identifier of the change set""",
    )
    fun query(
        @NotBlank
        @Argument(index = 0)
        connectionString: String,
        @NotBlank
        @Argument(index = 1) globalUniqueChangeId: String,
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
