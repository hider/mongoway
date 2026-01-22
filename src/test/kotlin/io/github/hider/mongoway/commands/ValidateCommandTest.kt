package io.github.hider.mongoway.commands

import io.github.hider.mongoway.errors.ChangeValidationException
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.test.context.ActiveProfiles
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@ActiveProfiles("shell")
class ValidateCommandTest(
    @Autowired val command: ValidateCommand
) {

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `empty change log`(output: CapturedOutput) {
        val ex = assertFailsWith<ChangeValidationException> {
            command.validate("")
        }
        assertEquals("Validation failed. See the error(s) above for details.", ex.message)
        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out.lines()).isEqualTo(
                    listOf(
                        "Started change log validation command...",
                        "[30;41;1m Error [0m Error while processing change log []: path must not be blank at index 1.",
                        "[30;41;1m Error [0m 1 change log(s) failed out of 1.",
                        "[30;41;1m Error [0m No change sets were processed.",
                        ""
                    )
                )
            }
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `blank change log`(output: CapturedOutput) {
        val ex = assertFailsWith<ChangeValidationException> {
            command.validate(" ", " \t", "\n")
        }
        assertEquals("Validation failed. See the error(s) above for details.", ex.message)
        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out.lines()).isEqualTo(
                    listOf(
                        "Started change log validation command...",
                        "[30;41;1m Error [0m Error while processing change log [ ]: path must not be blank at index 1.",
                        "[30;41;1m Error [0m Error while processing change log [ 	]: path must not be blank at index 2.",
                        "[30;41;1m Error [0m Error while processing change log [",
                        "]: path must not be blank at index 3.",
                        "[30;41;1m Error [0m 3 change log(s) failed out of 3.",
                        "[30;41;1m Error [0m No change sets were processed.",
                        "",
                    )
                )
            }
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `duplicated globalUniqueChangeId`(output: CapturedOutput) {
        val path = "src/test/resources/validate/test 01.json"
        val ex = assertFailsWith<ChangeValidationException> {
            command.validate(path)
        }
        assertEquals("Validation failed. See the error(s) above for details.", ex.message)
        val absolutePath = Path.of(path).absolutePathString()
        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out.lines()).isEqualTo(
                    listOf(
                        "Started change log validation command...",
                        "Processing change log file $absolutePath",
                        "[30;41;1m Error [0m Error while processing change log [$path]: globalUniqueChangeId 'id 1' is found multiple times, but globalUniqueChangeId should be unique across change sets.",
                        "[30;41;1m Error [0m Error while processing change log [$path]: globalUniqueChangeId 'id 1' is found multiple times, but globalUniqueChangeId should be unique across change sets.",
                        "[30;41;1m Error [0m 1 change log(s) failed out of 1.",
                        "[30;41;1m Error [0m 2 change set(s) failed out of 3.",
                        "",
                    )
                )
            }
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `invalid change log file`(output: CapturedOutput) {
        val path = "not exists"
        val ex = assertFailsWith<ChangeValidationException> {
            command.validate(path)
        }
        assertEquals("Validation failed. See the error(s) above for details.", ex.message)
        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out.lines()).isEqualTo(
                    listOf(
                        "Started change log validation command...",
                        "[30;41;1m Error [0m Error while processing change log [not exists]: file [${pwd}not exists] is not readable. Ensure the resource exists.",
                        "[30;41;1m Error [0m 1 change log(s) failed out of 1.",
                        "[30;41;1m Error [0m No change sets were processed.",
                        "",
                    )
                )
            }
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `forbidden collection name`(output: CapturedOutput) {
        val path = "src/test/resources/update/validation 07.json"
        val ex = assertFailsWith<ChangeValidationException> {
            command.validate(path)
        }
        assertEquals("Validation failed. See the error(s) above for details.", ex.message)
        val absolutePath = Path.of(path).absolutePathString()
        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out.lines()).isEqualTo(
                    listOf(
                        "Started change log validation command...",
                        "Processing change log file $absolutePath",
                        "[30;41;1m Error [0m changeSet[globalUniqueChangeId=globalUniqueChangeId 1].targetCollection must not be 'database_changelog'.",
                        "[30;41;1m Error [0m 1 change log(s) failed out of 1.",
                        "[30;41;1m Error [0m No change sets were processed.",
                        "",
                    )
                )
            }
    }

    @Test
    @ExtendWith(OutputCaptureExtension::class)
    fun `insertOne successful`(output: CapturedOutput) {
        command.validate("src/test/resources/validate/test 02.json", "src/test/resources/validate/test 03.json")
        await()
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted {
                assertThat(output.out.normalizeLineEndings()).endsWith("""
[30;42;1m  OK   [0m 2 change log(s) processed successfully.
[30;42;1m  OK   [0m 4 change set(s) processed successfully.
"""
                )
            }
    }
}
