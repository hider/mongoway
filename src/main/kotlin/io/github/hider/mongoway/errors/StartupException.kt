package io.github.hider.mongoway.errors

class StartupException(
    message: String,
    cause: Throwable? = null
) : MongoWayException(message, cause)
