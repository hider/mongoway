package io.github.hider.mongoway.commands

import io.github.hider.mongoway.*
import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.core.io.FileSystemResourceLoader
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option
import java.time.LocalDateTime


@IdeaCommandDetection
@Command(group = COMMAND_GROUP)
class RollbackCommand(
    private val mongoWayResourceLoader: FileSystemResourceLoader,
    private val connection: MongoConnection,
    private val changelogProcessor: ChangelogProcessor,
    private val buildProperties: BuildProperties,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val username = System.getProperty("user.name", "")
        .ifBlank { throw StartupException("Username is not available") }
    private val hostname: String? = System.getenv("HOSTNAME") ?: System.getenv("COMPUTERNAME")

    @Command(description = "Rollback change set by globalUniqueChangeId.")
    fun rollback(
        @Option(required = true, description = CS_DESCRIPTION) connectionString: String,
        @Option(required = true) globalUniqueChangeId: String,
    ) {
        val now = LocalDateTime.now()
        connection.useDatabase(connectionString) { db, databaseChangelog ->
            val changelog = databaseChangelog.findFirstByChangeSetGlobalUniqueChangeIdOrderByIdDesc(globalUniqueChangeId)
            if (changelog == null) {
                log.warn("Change set with globalUniqueChangeId '{}' not found.", globalUniqueChangeId)
                return@useDatabase
            }
            if (changelog.rollback?.rolledBackChangelogId != null) {
                throw IllegalStateException("changeSet[globalUniqueChangeId=$globalUniqueChangeId] already rolled back at ${changelog.executed.at.formatted()} by ${changelog.executed.by}.")
            }
            val rollback = changelog.rollback?.change
            if (rollback == null) {
                log.warn("No rollback change set found for globalUniqueChangeId '{}'.", globalUniqueChangeId)
                return@useDatabase
            }
            val description = if (changelog.changeSet.description == null) {
                "Rollback change set for database changelog '${changelog.id}'."
            } else {
                "Rollback change set for database changelog '${changelog.id}'.\nOriginal description: '${changelog.changeSet.description}'"
            }
            val rollbackChangeSet = ChangeSet(
                changelog.changeSet.globalUniqueChangeId,
                changelog.changeSet.author,
                changelog.changeSet.targetCollection,
                rollback,
                null,
                description,
                null,
            )
            val changelogPath = mongoWayResourceLoader.getResource(changelog.executed.path)
            val hash = changelogProcessor.preValidate(rollbackChangeSet, changelogPath)
            log.info("changeSet[globalUniqueChangeId={}}] is executing...", rollbackChangeSet.globalUniqueChangeId)
            val collection = db.getCollection(rollbackChangeSet.targetCollection)
            rollbackChangeSet.change.execute(collection)
            val rollbackChangelog = databaseChangelog.save(
                DatabaseChangelog(
                    Executed(username, now, changelog.executed.path, hostname),
                    rollbackChangeSet,
                    hash,
                    buildProperties.version,
                    Rollback(
                        changelog.changeSet.change,
                        changelog.id,
                    )
                ),
            )
            changelog.rollback.changelogId = rollbackChangelog.id
            databaseChangelog.save(changelog)
            log.info("Rollback change set with globalUniqueChangeId '{}' executed successfully.", globalUniqueChangeId)
        }
    }
}
