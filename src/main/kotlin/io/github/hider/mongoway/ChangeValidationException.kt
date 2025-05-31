package io.github.hider.mongoway

class ChangeValidationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
