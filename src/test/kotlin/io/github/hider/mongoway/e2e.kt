package io.github.hider.mongoway

import org.assertj.core.api.Assertions.assertThat
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
        System.setProperty("spring.shell.interactive.enabled", "false")
        System.setProperty("spring.profiles.active", "shell")

        assertThrows<CommandExecutionException> {
            main(arrayOf("update", "mongodb://localhost:8080/test", "changelog.json"))
        }

        assertThat(output.err).startsWith(
            "\u001B[30;41;1m Error \u001B[0m Unable to connect to the MongoDB database. Ensure the specified server is running and accessible on your network. "
        )

        System.clearProperty("spring.profiles.active")
        System.clearProperty("spring.shell.interactive.enable")
    }
}
