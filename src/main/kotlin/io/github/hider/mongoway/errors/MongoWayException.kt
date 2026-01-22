package io.github.hider.mongoway.errors

sealed class MongoWayException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
