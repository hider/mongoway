package io.github.hider.mongoway.commands

import io.github.hider.mongoway.*
import io.github.hider.mongoway.MongoWayConfiguration.Env
import io.github.hider.mongoway.errors.ChangeValidationException
import io.github.hider.mongoway.errors.globalUniqueChangeIdViolationError
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.core.io.FileSystemResourceLoader
import org.springframework.shell.core.command.annotation.Argument
import org.springframework.shell.core.command.annotation.Command
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


private const val DESCRIPTION = "Execute change sets in the change log(s) against the database."

@Component
class UpdateCommand(
    private val mongoWayResourceLoader: FileSystemResourceLoader,
    private val buildProperties: BuildProperties,
    private val connection: MongoConnection,
    private val changelogProcessor: ChangelogProcessor,
    private val env: Env,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Command(
        group = COMMAND_GROUP,
        description = DESCRIPTION,
        help = """$DESCRIPTION
Usage: update <connectionString> <changelogPath> [changelogPaths...]
$CS_DESCRIPTION
$PATHS_DESCRIPTION""",
    )
    fun update(
        @Argument(index = 0)
        @NotBlank
        connectionString: String,
        @NotBlank
        @Argument(index = 1)
        changelogPath: String,
        @RemainingArguments
        vararg changelogPaths: String,
    ) {
        val paths = arrayOf(changelogPath, *changelogPaths)
        val now = LocalDateTime.now()
        val processedChangeSets = mutableSetOf<String>()
        val skippedChangeSets = mutableSetOf<String>()
        connection.useDatabase(connectionString) { db, databaseChangelog ->
            changelogProcessor.process(paths, failFast = true).forEach { (path, changeSetResult) ->
                val changeSet = changeSetResult.getOrThrow()
                if (!processedChangeSets.add(changeSet.globalUniqueChangeId)) {
                    throw ChangeValidationException(
                        globalUniqueChangeIdViolationError(
                            path,
                            changeSet.globalUniqueChangeId
                        )
                    )
                }
                val changelogResource = mongoWayResourceLoader.getResource(path)
                val hash = changelogProcessor.preValidate(changeSet, changelogResource)
                val executeChangeSet = {
                    log.info("changeSet[globalUniqueChangeId={}}] is executing...", changeSet.globalUniqueChangeId)
                    val collection = db.getCollection(changeSet.targetCollection)
                    val executionResult = changeSet.change.execute(collection)
                    val executed = Executed(
                        env.username,
                        now,
                        changelogResource.uri.toString(),
                        env.hostname,
                    )
                    val rollbackChange = changeSet.rollbackChange ?: executionResult.rollback
                    val rollback = rollbackChange?.let { Rollback(it) }
                    val changelog = databaseChangelog.save(
                        DatabaseChangelog(
                            executed,
                            changeSet,
                            hash,
                            buildProperties.version ?: "unknown",
                            rollback
                        ),
                    )
                    log.debug("Database changelog with _id '{}' saved successfully.", changelog.id)
                }
                log.debug("changeSet[globalUniqueChangeId={}}] find in {}.", changeSet.globalUniqueChangeId, changeSet.targetCollection)
                val found = databaseChangelog.findByChangeSetGlobalUniqueChangeIdAndRollbackRolledBackChangelogIdIsNullAndRollbackChangelogIdIsNullOrderByIdDesc(changeSet.globalUniqueChangeId)
                if (found == null) {
                    executeChangeSet()
                } else if (found.hash != hash) {
                    if (changeSet.run?.onChange == true) {
                        executeChangeSet()
                    } else {
                        throw ChangeValidationException("Change detected for globalUniqueChangeId='${changeSet.globalUniqueChangeId}' which is already executed at ${found.executed.at.formatted()} by ${found.executed.by} with different content but this change set is not re-runnable (change.run.onChange property is unset or false).")
                    }
                } else if (changeSet.run?.always == true) {
                    log.info(
                        "changeSet[globalUniqueChangeId={}}] will be executed again because change.run.always property is true.",
                        changeSet.globalUniqueChangeId
                    )
                    executeChangeSet()
                } else {
                    log.info(
                        "changeSet[globalUniqueChangeId={}}] already executed at {} by {}. Skipping.",
                        changeSet.globalUniqueChangeId,
                        found.executed.at.formatted(),
                        found.executed.by
                    )
                    skippedChangeSets.add(changeSet.globalUniqueChangeId)
                }
            }
            var skippedText = ""
            if (skippedChangeSets.isNotEmpty()) {
                skippedText = "\nSkipped ${skippedChangeSets.size} change sets, including ${
                    skippedChangeSets.take(5).joinToString(", ")
                }"
            }
            log.info("Successfully processed {} change sets.{}", processedChangeSets.size, skippedText)
        }
    }
}

private val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
internal fun LocalDateTime.formatted() = format(formatter)
