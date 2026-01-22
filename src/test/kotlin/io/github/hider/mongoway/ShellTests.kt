package io.github.hider.mongoway

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.shell.core.command.CommandExecutionException
import org.springframework.shell.test.ShellTestClient
import org.springframework.shell.test.autoconfigure.ShellTestClientAutoConfiguration
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.mongodb.MongoDBContainer
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs


@SpringBootTest(classes = [Config::class])
@ImportAutoConfiguration(value = [ShellTestClientAutoConfiguration::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("shell")
class ShellTests(
    @Autowired testMongo: MongoDBContainer,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired val client: ShellTestClient,
) {
    val connectionString = testMongo.connectionString + "/shell_tests"

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `read from stdin and fail`(output: CapturedOutput) {
        val stdin = System.`in`
        System.setIn("[{}]".byteInputStream())
        val ex = assertFailsWith<CommandExecutionException> {
            client.sendCommand("update $connectionString -")
        }
        System.setIn(stdin)
        val cwd = Path("").absolutePathString()
        assertEquals(
            "Error while processing change log [$cwd]: changeSet[0].globalUniqueChangeId property is required but it is missing or null.",
            assertIs<InvocationTargetException>(ex.cause).targetException.message
        )
        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out).isEqualTo("Reading change log from standard input..." + System.lineSeparator())
            }
    }

    @Test
    fun `jakarta validation`() {
        val screen = client.sendCommand("update '' ")
        assertEquals(
            listOf("The following constraints were not met:", "\t--changelogPath: must not be blank"),
            screen.lines
        )
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `rollback change set not found`(output: CapturedOutput) {
        client.sendCommand("rollback $connectionString noSuchChangeSetId")

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
        client.sendCommand("update $connectionString src/test/resources/rollback/test04.json")
        client.sendCommand("rollback $connectionString \"rollback 4 1\"")

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
        client.sendCommand("query $connectionString noSuchChangeSetId")

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
        val changelogPath = Path("src/test/resources/update/insertOne/test02.json").absolute()
        client.sendCommand("update $connectionString ${changelogPath.invariantSeparatorsPathString}")

        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out.lines()).containsExactly(
                    "Processing change log file ${changelogPath.pathString}",
                    "changeSet[globalUniqueChangeId=update insertOne 2 1}] is executing...",
                    "changeSet[globalUniqueChangeId=update insertOne 2 2}] is executing...",
                    "changeSet[globalUniqueChangeId=update insertOne 2 3}] is executing...",
                    "Successfully processed 3 change sets.",
                    "",
                )
            }
    }
}
