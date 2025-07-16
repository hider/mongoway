package io.github.hider.mongoway.commands

import java.nio.file.FileSystems

fun String.normalizeLineEndings(): String = replace("\r\n", "\n")
val pwd = System.getProperty("user.dir") + FileSystems.getDefault().separator
