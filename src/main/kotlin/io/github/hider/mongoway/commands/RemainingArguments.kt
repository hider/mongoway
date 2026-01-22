package io.github.hider.mongoway.commands

import io.github.hider.mongoway.Workaround


/**
 * @see org.springframework.shell.core.command.annotation.Arguments
 */
@Workaround
@Target(AnnotationTarget.VALUE_PARAMETER)
internal annotation class RemainingArguments
