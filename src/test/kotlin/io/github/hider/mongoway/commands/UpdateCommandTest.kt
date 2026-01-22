package io.github.hider.mongoway.commands

import io.github.hider.mongoway.*
import io.github.hider.mongoway.errors.ChangeValidationException
import io.github.hider.mongoway.errors.StartupException
import org.assertj.core.api.Assertions.assertThat
import org.bson.BsonInvalidOperationException
import org.bson.Document
import org.bson.json.JsonParseException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.mongodb.MongoDBContainer
import java.lang.reflect.InvocationTargetException
import kotlin.test.*

@SpringBootTest(classes = [Config::class])
class UpdateCommandTest(
    @Autowired testMongo: MongoDBContainer,
    @Autowired config: Config,
    @Autowired val connection: MongoConnection,
    @Autowired val command: UpdateCommand,
) {
    val connectionString = testMongo.connectionString + '/' + config.databaseName

    @Test
    fun `database name is missing from the connection string`() {
        val ex = assertFailsWith<StartupException> {
            command.update("mongodb://mongo-host:80", "not exists")
        }
        assertEquals("Database name is required in the connection string.", ex.message)
    }

    @Test
    fun `invalid change log file`() {
        val ex = assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "not exists")
        }
        assertEquals(
            "Error while processing change log [not exists]: file [${pwd}not exists] is not readable. Ensure the resource exists.",
            ex.message
        )
    }

    @Test
    fun `change log is not an array`() {
        val path = "src/test/resources/update/test-parse-error 1.json"
        val ex = assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }
        assertEquals("Error while processing change log [$path]: change log should be a JSON array.", ex.message)
        assertIs<ChangeValidationException>(ex.cause).also {
            assertEquals("Change log should be a JSON array.", it.message)
            assertIs<BsonInvalidOperationException>(it.cause)
        }
    }

    @Test
    fun `change set has missing property`() {
        val path = "src/test/resources/update/test parse error 2.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also { ex ->
            assertEquals(
                "Error while processing change log [$path]: changeSet[0].change.filter property is required but it is missing or null.",
                ex.message
            )
            assertIs<ChangeValidationException>(ex.cause).also {
                assertEquals("changeSet[0].change.filter property is required but it is missing or null.", it.message)
                assertIs<InvocationTargetException>(it.cause)
            }
        }
    }

    @Test
    fun `change set type error`() {
        val path = "src/test/resources/update/test parse error 3.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also { ex ->
            assertEquals(
                "Error while processing change log [$path]: changeSet[0].change.expectedModifiedCount property has invalid type: expected 'numeric', got 'string'.",
                ex.message
            )
            assertIs<ChangeValidationException>(ex.cause).also {
                assertEquals("changeSet[0].change.expectedModifiedCount property has invalid type: expected 'numeric', got 'string'.", it.message)
                assertIs<BsonInvalidOperationException>(it.cause)
            }
        }
    }

    @Test
    fun `forbidden collection name`() {
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/validation 07.json")
        }.also {ex ->
            assertEquals(
                "changeSet[globalUniqueChangeId=globalUniqueChangeId 1].targetCollection must not be 'database_changelog'.",
                ex.message
            )
        }
    }

    @Test
    fun `single document validation`() {
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/validation 01.json")
        }.also { ex ->
            assertEquals(
                "changeSet[globalUniqueChangeId=update validation 01] validation error: either change.document or change.externalDocumentPath must be provided.",
                ex.message
            )
        }

        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/validation 02.json")
        }.also { ex ->
            assertEquals(
                "changeSet[globalUniqueChangeId=update validation 02] validation error: only the change.document or the change.externalDocumentPath can be specified (not both).",
                ex.message
            )
        }

        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/validation 03.json")
        }.also { ex ->
            assertEquals(
                "changeSet[globalUniqueChangeId=update validation 03] validation error: change.relativeToChangelog is only valid when change.externalDocumentPath is provided.",
                ex.message
            )
        }
    }

    @Test
    fun `multiple documents validation`() {
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/validation 04.json")
        }.also { ex ->
            assertEquals(
                "changeSet[globalUniqueChangeId=update validation 04] validation error: either change.documents or change.externalDocumentsPath must be provided.",
                ex.message
            )
        }

        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/validation 05.json")
        }.also { ex ->
            assertEquals(
                "changeSet[globalUniqueChangeId=update validation 05] validation error: only the change.documents or the change.externalDocumentsPath can be specified (not both).",
                ex.message
            )
        }

        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/validation 06.json")
        }.also { ex ->
            assertEquals(
                "changeSet[globalUniqueChangeId=update validation 06] validation error: change.relativeToChangelog is only valid when change.externalDocumentsPath is provided.",
                ex.message
            )
        }
    }

    @Test
    fun `duplicated globalUniqueChangeId`() {
        val path = "src/test/resources/update/test04.json"
        val ex = assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }
        assertEquals(
            "Error while processing change log [$path]: globalUniqueChangeId 'update 4 duplicate' is found multiple times, but globalUniqueChangeId should be unique across change sets.",
            ex.message
        )
    }

    @Test
    fun `execute insertOne`() {
        val path = "src/test/resources/update/insertOne/test02.json"
        command.update(connectionString, path)
        connection.useDatabase(connectionString) { db, repo ->
            val documents = db.getCollection(CHANGELOG_COLLECTION_NAME)
                .find(Document(mapOf("changeSet.change.document.documentId" to "update 2")))
            assertThat(documents).hasSize(2)

            val collection = db.getCollection("test_02")
            val all = collection.find()
            assertThat(all).hasSize(3)
            val firstInsert =
                repo.findByChangeSetGlobalUniqueChangeIdAndRollbackRolledBackChangelogIdIsNullAndRollbackChangelogIdIsNullOrderByIdDesc(
                    "update insertOne 2 1"
                )
            val secondInsert =
                repo.findByChangeSetGlobalUniqueChangeIdAndRollbackRolledBackChangelogIdIsNullAndRollbackChangelogIdIsNullOrderByIdDesc(
                    "update insertOne 2 2"
                )
            assertNotNull(firstInsert)
            assertNotNull(secondInsert)
            assertThat(firstInsert.executed.path).endsWith(path)
            assertThat(secondInsert.executed.path).endsWith(path)
            val iterator = all.iterator()
            val doc1 = iterator.next()
            assertEquals(1, doc1["foo"])
            assertEquals(2, (doc1["bar"] as Document)["baz"])
            assertEquals(
                Rollback(DeleteOne(Document(mapOf("_id" to doc1["_id"])), true)),
                firstInsert.rollback
            )
            val doc2 = iterator.next()
            assertEquals(2, doc2["foo"])
            assertEquals(3, (doc2["bar"] as Document)["baz"])
            assertEquals(
                Rollback(DeleteOne(Document(mapOf("_id" to doc2["_id"])), true)),
                secondInsert.rollback
            )
            assertHash(firstInsert, "8e0ab794d215cc19debeed47c88b5e91a0cbe7b39a68a4f1ddb5156c696e5d4bbee10e57248199042a3713cded53a762350e3c699bbbe15e791af58fbefc7c26")
            assertHash(secondInsert, "e7d6d98364b4d38d8c94e23069c467050bf7a34ba45c603f26c3063e0b5586cd522c3bffe67659bd1d756459695ce31d2bb510a2df2793e7ff47b49c14a44d17")
        }
    }

    @Test
    fun `insertOne external file`() {
        val path = "src/test/resources/update/insertOne/test03.json"
        command.update(connectionString, path)
        connection.useDatabase(connectionString) { db, repo ->
            val changelog = repo.findFirstByChangeSetGlobalUniqueChangeIdOrderByIdDesc("update test 03 1")
            assertNotNull(changelog)
            assertThat(changelog.executed.path).endsWith(path)
            val collection = db.getCollection("test_03")
            assertEquals(1, collection.countDocuments())
            val doc = collection.find().iterator().next()
            assertEquals("value", (doc["ext doc"] as Document)["some"])
            assertEquals(
                Rollback(DeleteOne(Document(mapOf("_id" to doc["_id"])), true)),
                changelog.rollback
            )
            assertHash(changelog, "3b1fe5fc325fb6d36fb21c55d8da307057feea4b3e30e0d84b4d47b8dc2ab87918a3d768051bc869752b9b25a622216a888478baa7ed1b21df1d24df7fa901fb")
        }
    }

    @Test
    fun `insertOne external file run twice`() {
        val path = "src/test/resources/update/insertOne/test03.json"
        command.update(connectionString, path)
        command.update(connectionString, path)
        connection.useDatabase(connectionString) { db, repo ->
            val changelog = repo.findFirstByChangeSetGlobalUniqueChangeIdOrderByIdDesc("update test 03 1")
            assertNotNull(changelog)
            val collection = db.getCollection("test_03")
            assertEquals(1, collection.countDocuments())
        }
    }

    @Test
    fun `insertOne non-relative external file`() {
        val path = "src/test/resources/update/insertOne/test04.json"
        command.update(connectionString, path)
        connection.useDatabase(connectionString) { db, repo ->
            val changelog = repo.findFirstByChangeSetGlobalUniqueChangeIdOrderByIdDesc("update test 04 1")
            assertNotNull(changelog)
            val collection = db.getCollection("test_04")
            assertEquals(1, collection.countDocuments())
            val iterator = collection.find().iterator()
            val doc = iterator.next()
            assertEquals("ext2", doc["ext2"])
        }
    }

    @Test
    fun `execute insertOne run onChange`() {
        command.update(connectionString, "src/test/resources/update/insertOne/test02.json")
        command.update(connectionString, "src/test/resources/update/insertOne/test02 2.json")
        connection.useDatabase(connectionString) { db, _ ->
            val collection = db.getCollection("test_02")
            val all = collection.find()
            assertThat(all).hasSize(4)
        }
    }

    @Test
    fun `execute insertOne run always`() {
        val path = "src/test/resources/update/insertOne/test11.json"
        command.update(connectionString, path)
        command.update(connectionString, path)
        connection.useDatabase(connectionString) { db, _ ->
            val collection = db.getCollection("test_11")
            val all = collection.find()
            assertThat(all).hasSize(2)
        }
    }

    @Test
    fun `execute insertOne not expected change`() {
        command.update(connectionString, "src/test/resources/update/insertOne/test02.json")
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/insertOne/test02 3.json")
        }.also {
            assertThat(it.message).endsWith(" by ${System.getProperty("user.name")} with different content but this change set is not re-runnable (change.run.onChange property is unset or false).")
        }
        connection.useDatabase(connectionString) { db, _ ->
            val collection = db.getCollection("test_02")
            val all = collection.find()
            assertThat(all).hasSize(3)
        }
    }

    @Test
    fun `execute updateOne`() {
        val path = "src/test/resources/update/updateOne/test05.json"
        command.update(connectionString, path)
        connection.useDatabase(connectionString) { db, repo ->
            val collection = db.getCollection("test_05")
            assertEquals(1, collection.countDocuments())
            val iterator = collection.find().iterator()
            val doc = iterator.next()
            assertEquals("lorem ipsum dolor sit amet", doc["value 1"])
            assertEquals("😎 😀 🦄 🍕 🚀 🐙 🎉", doc["value 2"])
            assertRollback(repo, "update test 05 2", UpdateOne(
                Document.parse("""{
                "_id": "update test 05 1 _id",
                "value 1": "lorem ipsum dolor sit amet",
                "value 2": "star wars"
            }"""),
                null,
                Document.parse("""{
                "value 1": "lorem ipsum dolor sit amet"
            }"""),
                null,
                null,
            )).also {
                assertHash(it, "dd6fa7eb9dfc0b1bde2ddc31acfa10b7583a0742bca13723b6092addacdaad51a53fd46df224f9713cb3538a7e12ed0134b8d47f628f8ea97e6b8c4ce2cc4f0a")
            }
        }
    }

    @Test
    fun `execute updateOne without match`() {
        val path = "src/test/resources/update/updateOne/test05 2.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also {
            assertEquals(
                "no document matched the filter and change.failWithoutUpdate is true (default is true)",
                it.message
            )
        }
    }

    @Test
    fun `execute updateOne without filter`() {
        val path = "src/test/resources/update/updateOne/test05 3.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also {
            assertEquals(
                "changeSet[globalUniqueChangeId=update test 05 4] validation error: filter is required for updateOne action.",
                it.message
            )
        }
    }

    @Test
    fun `execute updateOne with converted ObjectId filter`() {
        val path = "src/test/resources/update/updateOne/test12.json"
        command.update(connectionString, path)
    }

    @Test
    fun `execute insertMany`() {
        val path = "src/test/resources/update/insertMany/test06.json"
        command.update(connectionString, path)
        connection.useDatabase(connectionString) { db, repo ->
            val collection = db.getCollection("test_06")
            assertEquals(9, collection.countDocuments())
            val ids = mutableListOf<Any>()
            collection.find().forEachIndexed { index, doc ->
                assertEquals("value $index", doc["key $index"])
                val id = assertNotNull(doc["_id"])
                ids.add(id)
            }
            val rollbackKeys = IntRange(1, 3).flatMap { i ->
                val changelog = repo.findFirstByChangeSetGlobalUniqueChangeIdOrderByIdDesc("update test 06 $i")
                val rollbackChange = assertIs<DeleteMany>(changelog?.rollback?.change)
                val filter = assertIs<Map<String, Map<String, List<*>>>>(rollbackChange.filter)
                assertNotNull(filter["_id"]?.get($$"$in"))
            }
            assertThat(rollbackKeys).containsExactlyInAnyOrderElementsOf(ids)
        }
    }

    @Test
    fun `execute insertMany error`() {
        val path = "src/test/resources/update/insertMany/error01.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also {
            assertEquals(
                "changeSet[globalUniqueChangeId=update insertMany error 1] validation error: insertMany documents must not be empty.",
                it.message
            )
        }
    }

    @Test
    fun `insertMany fails with invalid external files`() {
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/insertMany/test13.json")
        }.also {
            assertEquals(
                "changeSet[globalUniqueChangeId=update test 13 1] validation error: external document [src/test/resources/update/insertMany/ext3.json] should be a JSON array.",
                it.message
            )
            assertIs<BsonInvalidOperationException>(it.cause?.cause?.cause)
        }

        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/insertMany/test14.json")
        }.also {
            assertEquals(
                "changeSet[globalUniqueChangeId=update test 14 1] validation error: external document [src/test/resources/update/insertMany/ext4.json.bad] should be a JSON array.",
                it.message
            )
            assertIs<JsonParseException>(it.cause?.cause?.cause)
        }

        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/insertMany/test15.json")
        }.also {
            assertEquals(
                "changeSet[globalUniqueChangeId=update test 15 1] validation error: external document [src/test/resources/update/insertMany/non_existing.json] is not readable. Ensure the resource exists.",
                it.message
            )
        }
    }

    @Test
    fun `execute updateMany`() {
        val path = "src/test/resources/update/updateMany/test07.json"
        command.update(connectionString, path)
        connection.useDatabase(connectionString) { db, repo ->
            val collection = db.getCollection("test_07")
            assertEquals(4, collection.countDocuments())
            val iterator = collection.find().iterator()
            assertEquals(47, iterator.next()["new prop"])
            assertEquals(2, iterator.next().size)
            assertEquals(47, iterator.next()["new prop"])
            assertEquals(200, iterator.next()["counter"])
            repo.findFirstByChangeSetGlobalUniqueChangeIdOrderByIdDesc("update test 07 2")
                .also { changelog ->
                    assertNull(changelog?.rollback)
                }
        }
    }

    @Test
    fun `execute updateMany without match`() {
        val path = "src/test/resources/update/updateMany/error01.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also {
            assertEquals(
                "no document matched the filter and change.failWithoutUpdate is true (default is true)",
                it.message
            )
        }
    }

    @Test
    fun `execute updateMany without filter`() {
        val path = "src/test/resources/update/updateMany/error02.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also {
            assertEquals(
                "changeSet[globalUniqueChangeId=update updateMany error 2] validation error: filter is required for updateMany action.",
                it.message
            )
        }
    }

    @Test
    fun `execute updateMany count mismatch`() {
        val path = "src/test/resources/update/updateMany/error03.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also {
            assertEquals(
                "expected modified count 2 but actual modified count 3",
                it.message
            )
        }
    }

    @Test
    fun `execute deleteOne`() {
        val path = "src/test/resources/update/deleteOne/test08.json"
        command.update(connectionString, path)
        connection.useDatabase(connectionString) { db, repo ->
            val collection = db.getCollection("test_08")
            assertEquals(2, collection.countDocuments())
            assertRollback(repo, "update test 08 2", InsertOne(
                Document.parse("""{"_id": "update test 08 1 _id","key 2": "value 2"}"""),
                null,
                null,
            ))
        }
    }

    @Test
    fun `execute deleteOne without match`() {
        val path = "src/test/resources/update/deleteOne/error01.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also {
            assertEquals(
                "no document matched the filter and change.failWithoutDelete is true (default is true)",
                it.message
            )
        }
    }

    @Test
    fun `execute deleteOne without filter`() {
        val path = "src/test/resources/update/deleteOne/error02.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also {
            assertEquals(
                "changeSet[globalUniqueChangeId=update deleteOne error 2] validation error: filter is required for deleteOne action.",
                it.message
            )
        }
    }

    @Test
    fun `execute deleteMany`() {
        val path = "src/test/resources/update/deleteMany/test16.json"
        command.update(connectionString, path)
        connection.useDatabase(connectionString) { db, _ ->
            val collection = db.getCollection("test_16")
            assertEquals(1, collection.countDocuments())
        }
    }

    @Test
    fun `execute deleteMany without match`() {
        val path = "src/test/resources/update/deleteMany/error01.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also {
            assertEquals(
                "no documents matched the filter and change.failWithoutDelete is true (default is true)",
                it.message
            )
        }
    }

    @Test
    fun `execute deleteMany without filter`() {
        val path = "src/test/resources/update/deleteMany/error02.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also {
            assertEquals(
                "changeSet[globalUniqueChangeId=update deleteMany error 2] validation error: filter is required for deleteMany action.",
                it.message
            )
        }
    }

    @Test
    fun `execute createIndex`() {
        val path = "src/test/resources/update/createIndex/test09.json"
        command.update(connectionString, path)
        connection.useDatabase(connectionString) { db, repo ->
            val collection = db.getCollection("test_09")
            collection.listIndexes()
                .first { it["name"] == "foo_1" }
                .also {
                    assertEquals(1, (it["key"] as Document)["foo"])
                }
            collection.listIndexes()
                .first { it["name"] == "bar_1_name" }
                .also {
                    assertEquals(true, it["unique"])
                    assertEquals(-1, (it["key"] as Document)["bar"])
                }
            assertRollback(repo, "update test 09 1", DropIndex("foo_1", null))
                .also {
                    assertHash(it, "becfe8366df9ba6b048b459e82ea34deb97ff2083e3d6279c8532b4b5ce455447a96aa6ec32641ec6e2750056d7dda08c17dab1f584d6180faa847cf20431315")
                }
            assertRollback(repo, "update test 09 2", DropIndex("bar_1_name", null))
                .also {
                    assertHash(it, "0a60a2bd2eb8ee2a8e72679b16174cfa8912e678bd957795288d9c06057ec4ac1e2e16d96d5912673b765c1ca547f54446fd8439b6753008a2543adc9e90d6ec")
                }
        }
    }

    @Test
    fun `execute createIndex without keys`() {
        val path = "src/test/resources/update/createIndex/error01.json"
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, path)
        }.also {
            assertEquals(
                "changeSet[globalUniqueChangeId=update createIndex error 1] validation error: keys is required for createIndex action.",
                it.message
            )
        }
    }

    @Test
    fun `execute dropIndex`() {
        val path = "src/test/resources/update/dropIndex/test10.json"
        command.update(connectionString, path)
        connection.useDatabase(connectionString) { db, repo ->
            val collection = db.getCollection("test_10")
            assertThat(collection.listIndexes()).hasSize(1)
            assertRollback(repo, "update test 10 3", null)
                .also {
                    assertHash(it, "ffdb6bbbd8f6d963e2ac0cc2c79bf6d1e53a572a06c1115fbe6e99a4c7de3f9cb3f5e1e8ca293cc8b673a76dc81c9fb441c75d509ade89211b0060ae001e0b70")
                }
            assertRollback(repo, "update test 10 4", CreateIndex(Document.parse("""{"bar":-1}"""), null))
                .also {
                    assertHash(it, "93b949b9592cedf6daba5c71025bdca74b07f872003095016e0fda0d7f5eb3aa41123455a15d947e43351dabb47e99b3d5eb920016a337b176b39ab38a3565d6")
                }
        }
    }

    @Test
    fun `execute dropIndex validation error`() {
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/dropIndex/error01.json")
        }.also {
            assertEquals(
                "changeSet[globalUniqueChangeId=update dropIndex error 1] validation error: only the change.name or the change.name can be specified (not both).",
                it.message
            )
        }
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/dropIndex/error02.json")
        }.also {
            assertEquals(
                "changeSet[globalUniqueChangeId=update dropIndex error 2] validation error: change.keys must not be empty.",
                it.message
            )
        }
        assertFailsWith<ChangeValidationException> {
            command.update(connectionString, "src/test/resources/update/dropIndex/error03.json")
        }.also {
            assertEquals(
                "changeSet[globalUniqueChangeId=update dropIndex error 3] validation error: either change.name or change.keys must be provided.",
                it.message
            )
        }
    }

    private fun assertRollback(repo: DatabaseChangelogRepository, globalUniqueChangeId: String, rollback: ChangeAction?): DatabaseChangelog {
        return assertNotNull(repo.findFirstByChangeSetGlobalUniqueChangeIdOrderByIdDesc(globalUniqueChangeId))
            .also { changelog ->
                if (rollback == null) {
                    assertNull(changelog.rollback)
                } else {
                    assertEquals(
                        Rollback(rollback),
                        changelog.rollback
                    )
                }
            }
    }

    private fun assertHash(
        changelog: DatabaseChangelog,
        expectedHash: String,
    ) {
        assertContentEquals(
            expectedHash.hexToByteArray(),
            changelog
                .hash
                .sha512
                .data,
            "Hash mismatch",
        )
    }
}
