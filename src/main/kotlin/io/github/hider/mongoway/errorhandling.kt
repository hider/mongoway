package io.github.hider.mongoway

import org.bson.BsonInvalidOperationException
import org.bson.codecs.configuration.CodecConfigurationException
import java.lang.reflect.InvocationTargetException


private val re = Regex("Invalid (\\w+) type, found: (\\w+)")

internal fun handleParseError(ex: CodecConfigurationException, counter: Int, parentProperty: String? = null): Exception {
    val cause = ex.cause
    if (cause is InvocationTargetException && cause.targetException is NullPointerException) {
        val missingProperty = cause.targetException.message?.substringAfterLast(" ")
        return ChangeValidationException(
            createErrorPrefix(
                counter,
                parentProperty,
                missingProperty
            ) + " property is required but it is missing or null.", cause
        )
    }
    if (cause is BsonInvalidOperationException) {
        if (ex.message?.startsWith("Unable to decode ") == true) {
            val problematicProperty = ex.message?.substringAfter("Unable to decode ")?.substringBefore(" for")
            val message = ex.cause?.message
            val errorPostFix = if (message != null) {
                val (expected, actual) = re.find(message)!!.destructured
                " property has invalid type: expected '$expected', got '${actual.lowercase()}'."
            } else {
                " property has invalid type."
            }
            return ChangeValidationException(
                createErrorPrefix(
                    counter,
                    parentProperty,
                    problematicProperty
                ) + errorPostFix, cause
            )
        }
    }
    if (cause is CodecConfigurationException) {
        val problematicProperty = if (ex.message?.startsWith("Unable to decode ") == true) {
            ex.message?.substringAfter("Unable to decode ")?.substringBefore(" for")
        } else null
        return handleParseError(cause, counter, problematicProperty)
    }
    return ex
}

internal fun globalUniqueChangeIdViolationError(path: String, globalUniqueChangeId: String) =
    "Error while processing change log [$path]: globalUniqueChangeId '${globalUniqueChangeId}' is found multiple times, but globalUniqueChangeId should be unique across change sets."

internal fun changelogError(path: String, cause: String?) =
    "Error while processing change log [$path]: ${cause?.uncapitalize()}"

private fun String.uncapitalize() = replaceFirstChar { it.lowercase() }

private fun createErrorPrefix(counter: Int, vararg properties: String?): String {
    return properties.filter { it != null }.joinToString(".", "changeSet[$counter].")
}
