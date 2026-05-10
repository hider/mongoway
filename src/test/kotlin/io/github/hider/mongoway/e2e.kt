package io.github.hider.mongoway

import io.github.hider.mongoway.errors.StartupException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.shell.core.command.CommandExecutionException
import kotlin.test.Test


@SpringBootTest(
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
    classes = [MongoWayApplication::class],
    properties = [
        "spring.shell.interactive.enabled=false",
        "spring.profiles.active=shell",
    ],
    args = []
)
@ExtendWith(OutputCaptureExtension::class)
class E2E1 {

    @Test
    fun `no args`(output: CapturedOutput) {
        assertThat(output.lines()).contains(
            "AVAILABLE COMMANDS",
            "Built-In Commands",
            "MongoWay",
            "\tupdate: Execute change sets in the change log(s) against the database.",
        )
    }
}

@ExtendWith(OutputCaptureExtension::class)
class E2E2 {

    @Test
    fun `wrong database`(output: CapturedOutput) {

        assertThrows<CommandExecutionException> {
            main(arrayOf("update", "mongodb://localhost:8080/test", "changelog.json"))
        }

        assertThat(output.err).startsWith(
            "\u001B[30;41;1m Error \u001B[0m Unable to connect to the MongoDB database. Ensure the specified server is running and accessible on your network. "
        )
    }

    @Test
    fun `usage error`(output: CapturedOutput) {
        assertThrows<StartupException> {
            main(arrayOf("validate", "\t"))
        }
        assertThat(output.out.lines())
            .contains(
                "The following 1 profile is active: \"shell\"",
            ).endsWith(
                "The following constraints were not met:",
                "\t--changelogPath: must not be blank",
                "",
            )
        assertThat(output.err).isEqualTo(
            "[30;41;1m Error [0m Invalid usage. See 'validate --help' for help." + System.lineSeparator()
        )
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            System.setProperty("spring.shell.interactive.enabled", "false")
            System.setProperty("spring.profiles.active", "shell")
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            System.clearProperty("spring.profiles.active")
            System.clearProperty("spring.shell.interactive.enabled")
        }
    }
}
