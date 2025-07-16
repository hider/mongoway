package io.github.hider.mongoway.commands

import io.github.hider.mongoway.*
import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.core.io.FileSystemResourceLoader
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


@IdeaCommandDetection
@Command(group = COMMAND_GROUP)
class UpdateCommand(
    private val mongoWayResourceLoader: FileSystemResourceLoader,
    private val buildProperties: BuildProperties,
    private val connection: MongoConnection,
    private val changelogProcessor: ChangelogProcessor,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val username = System.getProperty("user.name", "")
        .ifBlank { throw StartupException("Username is not available") }
    private val hostname: String? = System.getenv("HOSTNAME") ?: System.getenv("COMPUTERNAME")

    @Command(description = "Execute change sets in the change log on the database.")
    fun update(
        @Option(required = true, description = CS_DESCRIPTION)
        connectionString: String,
        @Option(
            required = true,
            label = "path1 [path2]...",
            description = PATHS_DESCRIPTION,
            // 1..127 is a workaround for a Spring Shell bug where arity = CommandRegistration.OptionArity.ONE_OR_MORE results in an integer overflow error
            arityMin = 1,
            arityMax = 127,
        )
        vararg changelogPaths: String,
    ) {
        val now = LocalDateTime.now()
        val processedChangeSets = mutableSetOf<String>()
        val skippedChangeSets = mutableSetOf<String>()
        connection.useDatabase(connectionString) { db, databaseChangelog ->
            try {
                changelogProcessor.process(changelogPaths, failFast = true).forEach { (changelogPath, changeSetResult) ->
                    val changeSet = changeSetResult.getOrThrow()
                    if (!processedChangeSets.add(changeSet.globalUniqueChangeId)) {
                        throw ChangeValidationException(globalUniqueChangeIdViolationError(changelogPath, changeSet.globalUniqueChangeId))
                    }
                    val changelogResource = mongoWayResourceLoader.getResource(changelogPath)
                    val hash = changelogProcessor.preValidate(changeSet, changelogResource)
                    val executeChangeSet = {
                        log.info("changeSet[globalUniqueChangeId={}}] is executing...", changeSet.globalUniqueChangeId)
                        val collection = db.getCollection(changeSet.targetCollection)
                        val executionResult = changeSet.change.execute(collection)
                        val executed = Executed(
                            username,
                            now,
                            changelogResource.uri.toString(),
                            hostname
                        )
                        val rollbackChange = changeSet.rollbackChange ?: executionResult.rollback
                        val rollback = rollbackChange?.let { Rollback(it) }
                        val changelog = databaseChangelog.save(
                            DatabaseChangelog(
                                executed,
                                changeSet,
                                hash,
                                buildProperties.version,
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
            } catch (e: Exception) {
                System.err.println(e.message)
                throw e
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
