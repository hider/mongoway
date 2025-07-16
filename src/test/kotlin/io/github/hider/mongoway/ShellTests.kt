package io.github.hider.mongoway

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.shell.test.ShellTestClient
import org.springframework.shell.test.autoconfigure.AutoConfigureShell
import org.springframework.shell.test.autoconfigure.AutoConfigureShellTestClient
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.MongoDBContainer
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString
import kotlin.test.Test


@SpringBootTest(classes = [Config::class])
@AutoConfigureShell
@AutoConfigureShellTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("shell")
class ShellTests {

    @Autowired
    lateinit var testMongo: MongoDBContainer
    @Autowired
    lateinit var client: ShellTestClient

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `read from stdin and fail`(output: CapturedOutput) {
        val connectionString = testMongo.connectionString + "/shell_tests"
        client
            .nonInterative("update", connectionString, "-")
            .write("foo" + System.lineSeparator())
            .run()

        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out).contains("Reading change log from standard input...")
                assertThat(output.err).contains("Error while processing change log [")
            }
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `rollback change set not found`(output: CapturedOutput) {
        val connectionString = testMongo.connectionString + "/shell_tests"
        client
            .nonInterative("rollback", connectionString, "noSuchChangeSetId")
            .run()

        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out).endsWith("Change set with globalUniqueChangeId 'noSuchChangeSetId' not found." + System.lineSeparator())
                assertThat(output.err).isEmpty()
            }
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `rollback not found`(output: CapturedOutput) {
        val connectionString = testMongo.connectionString + "/shell_tests"
        client
            .nonInterative("update", connectionString, "src/test/resources/rollback/test04.json")
            .run()
        client
            .nonInterative("rollback", connectionString, "rollback 4 1")
            .run()

        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out).endsWith("No rollback change set found for globalUniqueChangeId 'rollback 4 1'." + System.lineSeparator())
                assertThat(output.err).isEmpty()
            }
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `query change set not found`(output: CapturedOutput) {
        val connectionString = testMongo.connectionString + "/shell_tests"
        client
            .nonInterative("query", connectionString, "noSuchChangeSetId")
            .run()

        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out).endsWith("Change set with globalUniqueChangeId 'noSuchChangeSetId' not found." + System.lineSeparator())
                assertThat(output.err).isEmpty()
            }
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `update with absolute path`(output: CapturedOutput) {
        val connectionString = testMongo.connectionString + "/shell_tests"
        val changelogPath = Path("src/test/resources/update/insertOne/test02.json").absolute()
        client
            .nonInterative("update", connectionString, changelogPath.invariantSeparatorsPathString)
            .run()

        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out).contains(
                    "Processing change log file ${changelogPath.pathString}",
                    "Successfully processed 3 change sets."
                )
            }
    }
}
