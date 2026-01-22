package io.github.hider.mongoway.errors

class ChangeValidationException(
    message: String,
    cause: Throwable? = null
) : MongoWayException(message, cause)
