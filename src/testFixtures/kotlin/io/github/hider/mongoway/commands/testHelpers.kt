package io.github.hider.mongoway.commands

fun String.normalizeLineEndings(): String = replace("\r\n", "\n")
