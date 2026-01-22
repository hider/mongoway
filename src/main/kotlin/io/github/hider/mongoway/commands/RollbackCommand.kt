package io.github.hider.mongoway.commands

import io.github.hider.mongoway.*
import io.github.hider.mongoway.MongoWayConfiguration.Env
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.core.io.FileSystemResourceLoader
import org.springframework.shell.core.command.annotation.Argument
import org.springframework.shell.core.command.annotation.Command
import org.springframework.stereotype.Component
import java.time.LocalDateTime


private const val DESCRIPTION = "Execute change sets in the change log(s) against the database."

@Component
class RollbackCommand(
    private val mongoWayResourceLoader: FileSystemResourceLoader,
    private val connection: MongoConnection,
    private val changelogProcessor: ChangelogProcessor,
    private val buildProperties: BuildProperties,
    private val env: Env,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Command(
        group = COMMAND_GROUP,
        description = "Rollback change set by globalUniqueChangeId.",
        help = """$DESCRIPTION
Usage: rollback <connectionString> <globalUniqueChangeId>
$CS_DESCRIPTION
  [1mglobalUniqueChangeId[0m the unique identifier of the change set""",
    )
    fun rollback(
        @NotBlank
        @Argument(index = 0) connectionString: String,
        @NotBlank
        @Argument(index = 1) globalUniqueChangeId: String,
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
                    Executed(env.username, now, changelog.executed.path, env.hostname),
                    rollbackChangeSet,
                    hash,
                    buildProperties.version ?: "unknown",
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
