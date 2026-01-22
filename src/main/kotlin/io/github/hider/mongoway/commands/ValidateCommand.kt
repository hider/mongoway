package io.github.hider.mongoway.commands

import io.github.hider.mongoway.errors.ChangeValidationException
import io.github.hider.mongoway.ChangelogProcessor
import io.github.hider.mongoway.errors.changelogError
import io.github.hider.mongoway.errors.globalUniqueChangeIdViolationError
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResourceLoader
import org.springframework.shell.core.command.annotation.Argument
import org.springframework.shell.core.command.annotation.Command
import org.springframework.stereotype.Component


private const val DESCRIPTION = "Validates change sets in the change log(s)."
private const val ANSI_OK = "[30;42;1m  OK   [0m "
private const val ANSI_ERROR = "[30;41;1m Error [0m "

@Component
class ValidateCommand(
    private val mongoWayResourceLoader: FileSystemResourceLoader,
    private val changelogProcessor: ChangelogProcessor,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    @Command(
        group = COMMAND_GROUP,
        description = DESCRIPTION,
        help = """$DESCRIPTION
Usage: validate <changelogPath> [changelogPaths...]
$PATHS_DESCRIPTION""",
    )
    fun validate(
        @NotBlank
        @Argument(index = 0)
        changelogPath: String,
        @RemainingArguments
        vararg changelogPaths: String,
    ) {
        val paths = arrayOf(changelogPath, *changelogPaths)
        val processedChangelogs = mutableSetOf<String>()
        val processedChangeSets = mutableSetOf<String>()
        var detectedChangeSets = 0
        var error = false
        log.info("Started change log validation command...")
        try {
            changelogProcessor.process(paths, failFast = false).forEach { (path, changeSetResult) ->
                detectedChangeSets += 1
                changeSetResult
                    .onSuccess { changeSet ->
                        if (processedChangeSets.contains(changeSet.globalUniqueChangeId)) {
                            processedChangelogs.remove(path)
                            logError(globalUniqueChangeIdViolationError(path, changeSet.globalUniqueChangeId))
                        } else {
                            val changelogResource = mongoWayResourceLoader.getResource(path)
                            changelogProcessor.preValidate(changeSet, changelogResource)
                            processedChangeSets.add(changeSet.globalUniqueChangeId)
                            processedChangelogs.add(path)
                        }
                    }
                    .onFailure { ex ->
                        logError(changelogError(path, ex.message))
                    }
            }
        } catch (e: ChangeValidationException) {
            logError(e.message)
            error = true
        }
        if (processedChangelogs.size == paths.size) {
            logOk("${processedChangelogs.size} change log(s) processed successfully.")
        } else {
            logError("${paths.size - processedChangelogs.size} change log(s) failed out of ${paths.size}.")
            error = true
        }
        if (processedChangeSets.isEmpty()) {
            logError("No change sets were processed.")
            error = true
        } else if (processedChangeSets.size < detectedChangeSets) {
            logError("${detectedChangeSets - processedChangeSets.size} change set(s) failed out of ${detectedChangeSets}.")
            error = true
        } else {
            logOk("${processedChangeSets.size} change set(s) processed successfully.")
        }
        if (error) {
            throw ChangeValidationException("Validation failed. See the error(s) above for details.")
        }
    }

    private fun logOk(message: String) {
        log.info(ANSI_OK + message)
    }

    private fun logError(vararg messages: String?) {
        log.error(messages.joinToString("", ANSI_ERROR))
    }
}
