package io.github.hider.mongoway.commands

import io.github.hider.mongoway.Config
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.testcontainers.containers.MongoDBContainer
import java.util.concurrent.TimeUnit
import kotlin.test.Test

@SpringBootTest(classes = [Config::class])
class QueryCommandTest {

    @Autowired
    lateinit var testMongo: MongoDBContainer
    @Autowired
    lateinit var updateCommand: UpdateCommand
    @Autowired
    lateinit var command: QueryCommand
    @Autowired
    lateinit var config: Config

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `query by globalUniqueChangeId`(output: CapturedOutput) {
        val connectionString = testMongo.connectionString + '/' + config.databaseName
        updateCommand.update(connectionString, "src/test/resources/update/insertOne/test02.json")
        command.query(connectionString, "update insertOne 2 2")

        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out.normalizeLineEndings()).contains("""  "changeSet": {
    "globalUniqueChangeId": "update insertOne 2 2",
    "author": "test user",
    "targetCollection": "test_02",
    "change": {
      "document": {
        "documentId": "update 2",
        "foo": 2,
        "bar": {
          "baz": 3
        },
"""
                )
            }
    }
}
