package io.github.hider.mongoway.commands

import io.github.hider.mongoway.*
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.MongoDBContainer
import kotlin.test.*

@SpringBootTest(classes = [Config::class])
class RollbackCommandTest {

    @Autowired
    lateinit var testMongo: MongoDBContainer
    @Autowired
    lateinit var updateCommand: UpdateCommand
    @Autowired
    lateinit var command: RollbackCommand
    @Autowired
    lateinit var config: Config
    @Autowired
    lateinit var connection: MongoConnection

    @Test
    fun `insertOne rollback`() {
        val connectionString = testMongo.connectionString + '/' + config.databaseName
        updateCommand.update(connectionString, "rollback/test02.json")
        connection.useDatabase(connectionString) { db, repo ->
            val firstInsertedId = db.getCollection("rollback_documents")
                .find(Document(mapOf("documentId" to "rollback 2")))
                .first()
                ?.getObjectId("_id")
            val globalUniqueChangeId = "rollback 2 1"
            command.rollback(connectionString, globalUniqueChangeId)
            val changeLogsInserted = db.getCollection(CHANGELOG_COLLECTION_NAME)
                .find(Document(mapOf("changeSet.change.document.documentId" to "rollback 2")))
            assertThat(changeLogsInserted).hasSize(2)

            val documents = db.getCollection("rollback_documents")
                .find(Document(mapOf("documentId" to globalUniqueChangeId)))
            assertThat(documents).isEmpty()

            val rollbackChangelog = repo.findFirstByChangeSetGlobalUniqueChangeIdOrderByIdDesc(globalUniqueChangeId)
            assertNotNull(rollbackChangelog)
            assertNotNull(rollbackChangelog.rollback)
            assertNull(rollbackChangelog.rollback.changelogId)
            val firstId = changeLogsInserted.first()!!["_id"] as ObjectId
            assertEquals(firstId, rollbackChangelog.rollback.rolledBackChangelogId)
            assertEquals(
                ChangeSet(
                    globalUniqueChangeId,
                    "test user",
                    "rollback_documents",
                    DeleteOne(Document(mapOf("_id" to firstInsertedId)), true),
                    null,
                    "Rollback change set for database changelog '$firstId'.",
                    null
                ), rollbackChangelog.changeSet
            )
        }
    }

    @Test
    fun `insertOne rollback error`() {
        val connectionString = testMongo.connectionString + '/' + config.databaseName
        updateCommand.update(connectionString, "rollback/test01.json")
        val globalUniqueChangeId = "rollback 1 1"
        command.rollback(connectionString, globalUniqueChangeId)
        assertFailsWith<IllegalStateException> {
            command.rollback(connectionString, globalUniqueChangeId)
        }.also {
            assertThat(it.message).startsWith("changeSet[globalUniqueChangeId=$globalUniqueChangeId] already rolled back at ")
        }
    }

    @Test
    fun `insertOne rollback with description`() {
        val connectionString = testMongo.connectionString + '/' + config.databaseName
        updateCommand.update(connectionString, "rollback/test03.json")
        connection.useDatabase(connectionString) { db, repo ->
            val firstInsertedId = db.getCollection("rollback_documents")
                .find(Document(mapOf("documentId" to "rollback 3")))
                .first()
                ?.getObjectId("_id")
            val globalUniqueChangeId = "rollback 3 1"
            command.rollback(connectionString, globalUniqueChangeId)
            val changeLogsInserted = db.getCollection(CHANGELOG_COLLECTION_NAME)
                .find(Document(mapOf("changeSet.change.document.documentId" to "rollback 3")))
                .first()
            assertNotNull(changeLogsInserted)

            val rollbackChangelog = repo.findFirstByChangeSetGlobalUniqueChangeIdOrderByIdDesc(globalUniqueChangeId)
            assertNotNull(rollbackChangelog)
            assertNotNull(rollbackChangelog.rollback)
            assertNull(rollbackChangelog.rollback.changelogId)
            val firstId = changeLogsInserted["_id"] as ObjectId
            assertEquals(firstId, rollbackChangelog.rollback.rolledBackChangelogId)
            assertEquals(
                ChangeSet(
                    globalUniqueChangeId,
                    "test user",
                    "rollback_documents",
                    DeleteOne(Document(mapOf("_id" to firstInsertedId)), true),
                    null,
                    "Rollback change set for database changelog '$firstId'.\nOriginal description: 'provided original description'",
                    null
                ), rollbackChangelog.changeSet
            )
        }
    }

    @Test
    fun `updateMany with external file rollback`() {
        val connectionString = testMongo.connectionString + '/' + config.databaseName
        updateCommand.update(connectionString, "rollback/test05.json")
        command.rollback(connectionString, "rollback 5 2")
        connection.useDatabase(connectionString) { db, repo ->
            val documents =  db.getCollection("rollback_documents")
                .find(Document(mapOf("documentId" to "rollback 5 1")))
            assertThat(documents).satisfiesExactly(
                { assertThat(it["num"]).isEqualTo(150) },
                { assertThat(it["num"]).isEqualTo(350) },
                { assertThat(it["num"]).isEqualTo(550) },
            )
        }
    }
}
