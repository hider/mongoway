package io.github.hider.mongoway.commands

import io.github.hider.mongoway.ChangelogProcessor
import io.github.hider.mongoway.ChangeValidationException
import io.github.hider.mongoway.changelogError
import io.github.hider.mongoway.globalUniqueChangeIdViolationError
import org.jline.utils.AttributedStringBuilder
import org.jline.utils.AttributedStyle
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader
import org.springframework.shell.command.annotation.Command
import org.springframework.shell.command.annotation.Option

@IdeaCommandDetection
@Command(group = COMMAND_GROUP)
class ValidateCommand(
    private val resourceLoader: ResourceLoader,
    private val changelogProcessor: ChangelogProcessor,
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    @Command(description = "Validates change sets in the change log.")
    fun validate(
        @Option(
            required = true,
            label = "path1 [path2]...",
            description = PATHS_DESCRIPTION,
            // 1..127 is a workaround for a Spring Shell bug where arity = CommandRegistration.OptionArity.ONE_OR_MORE results in an integer overflow error
            arityMin = 1,
            arityMax = 127,
        )
        vararg changelogPaths: String,
    ) {
        var error = false
        val processedChangelogs = mutableSetOf<String>()
        val processedChangeSets = mutableSetOf<String>()
        var detectedChangeSets = 0
        log.info("Started change log validation command...")
        try {
            changelogProcessor.process(changelogPaths, failFast = false).forEach { (changelogPath, changeSetResult) ->
                detectedChangeSets += 1
                changeSetResult
                    .onSuccess { changeSet ->
                        if (processedChangeSets.contains(changeSet.globalUniqueChangeId)) {
                            processedChangelogs.remove(changelogPath)
                            logError(globalUniqueChangeIdViolationError(changelogPath, changeSet.globalUniqueChangeId))
                        } else {
                            val changelogResource = resourceLoader.getResource(changelogPath)
                            changelogProcessor.preValidate(changeSet, changelogResource)
                            processedChangeSets.add(changeSet.globalUniqueChangeId)
                            processedChangelogs.add(changelogPath)
                        }
                    }
                    .onFailure { ex ->
                        logError(changelogError(changelogPath, ex.message))
                    }
            }
        } catch (e: ChangeValidationException) {
            logError(e.message)
            error = true
        }
        if (changelogPaths.isEmpty() && processedChangelogs.isEmpty()) {
            logError("No change logs were processed.")
            error = true
        } else if (processedChangelogs.size == changelogPaths.size) {
            logOk("${processedChangelogs.size} change log(s) processed successfully.")
        } else {
            logError("${changelogPaths.size - processedChangelogs.size} change log(s) failed out of ${changelogPaths.size}.")
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
            log.error("Validation failed. See the error(s) above for details.")
            throw ChangeValidationException("Validation failed. See the error(s) above for details.")
        }
    }

    private fun logOk(message: String) {
        val formattedText = AttributedStringBuilder()
            .styled(AttributedStyle.BOLD.background(AttributedStyle.GREEN).foreground(AttributedStyle.BLACK), "  OK   ")
            .append(" ")
            .append(message)
        log.info(formattedText.toAnsi())
    }

    private fun logError(vararg messages: String?) {
        val formattedText = AttributedStringBuilder()
            .styled(AttributedStyle.BOLD.background(AttributedStyle.RED).foreground(AttributedStyle.BLACK), " Error ")
            .append(" ")
            .apply {
                for (it in messages) {
                    append(it)
                }
            }
        log.error(formattedText.toAnsi())
    }
}
