package io.github.hider.mongoway

import org.bson.types.Binary
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

const val CHANGELOG_COLLECTION_NAME = "database_changelog"

interface DatabaseChangelogRepository : CrudRepository<DatabaseChangelog, String> {
    fun findFirstByChangeSetGlobalUniqueChangeIdOrderByIdDesc(globalUniqueChangeId: String): DatabaseChangelog?
    fun findByChangeSetGlobalUniqueChangeIdAndRollbackRolledBackChangelogIdIsNullAndRollbackChangelogIdIsNullOrderByIdDesc(globalUniqueChangeId: String): DatabaseChangelog?
}

@Document(CHANGELOG_COLLECTION_NAME)
data class DatabaseChangelog(
    val executed: Executed,
    val changeSet: ChangeSet,
    val hash: Hash,
    val appVersion: String,
    val rollback: Rollback? = null,
    @Id val id: ObjectId? = null,
)

data class ChangeSet(
    val globalUniqueChangeId: String,
    val author: String,
    /**
     * A MongoDB collection where change will be applied.
     * It will be created if it does not exist.
     */
    val targetCollection: String,
    val change: ChangeAction,
    val rollbackChange: ChangeAction?,
    val description: String?,
    /**
     * Execution options.
     */
    val run: Run?,
)

data class Hash(
    val sha512: Binary,
)

data class Rollback(
    val change: ChangeAction,
    /**
     * ID of the normal change log that was rolled back.
     * Inclusive with `changelogId`.
     */
    val rolledBackChangelogId: ObjectId? = null,
    /**
     * ID of the rollback change log.
     * Null if this is change log is not rolled back.
     * Inclusive with `rolledBackChangelogId`.
     */
    var changelogId: ObjectId? = null,
)

data class Executed(
    val by: String,
    val at: LocalDateTime,
    val path: String,
    val onHost: String?,
)

data class Run(
    /**
     * Execute the change again if it was executed before and changed.
     */
    val onChange: Boolean?,
    /**
     * Execute the change again if it was not changed.
     * Can be used together with run.onChange=true.
     */
    val always: Boolean?,
)
