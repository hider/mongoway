package io.github.hider.mongoway.errors

import org.bson.BsonInvalidOperationException
import org.bson.codecs.configuration.CodecConfigurationException
import org.springframework.util.StringUtils


private val reInvalid = Regex("Invalid (\\w+) type, found: (\\w+)")
private val reUnable = Regex("Unable to decode (\\w+) for (\\w+) data class.")
private val reRequired = Regex("Required field '(\\w+)' is missing from the document for (\\w+) data class")

internal fun handleParseError(ex: CodecConfigurationException, counter: Int, parentProperty: String? = null): Exception {
    val cause = ex.cause
    val message = ex.message
    if (message != null) {
        val problematicProperty = reUnable.extractFirstGroup(message) ?: reRequired.extractFirstGroup(message)
        if (problematicProperty != null) {
            if (cause is BsonInvalidOperationException) {
                val errorPostFix = cause.message
                    ?.let { causeMessage ->
                        reInvalid.find(causeMessage)
                    }
                    ?.destructured
                    ?.let { (expected, actual) ->
                        " property has invalid type: expected '$expected', got '${actual.lowercase()}'."
                    }
                    ?: " property has invalid type."
                return ChangeValidationException(
                    createErrorPrefix(
                        counter,
                        parentProperty,
                        problematicProperty
                    ) + errorPostFix, cause
                )
            }
            if (cause is CodecConfigurationException) {
                return handleParseError(cause, counter, problematicProperty)
            }
            return ChangeValidationException(
                createErrorPrefix(
                    counter,
                    parentProperty,
                    problematicProperty
                ) + " required property is missing or null.", cause
            )
        }
    }
    return ex
}

internal fun globalUniqueChangeIdViolationError(path: String, globalUniqueChangeId: String) =
    "Error while processing change log [$path]: globalUniqueChangeId '${globalUniqueChangeId}' is found multiple times, but globalUniqueChangeId should be unique across change sets."

internal fun changelogError(path: String, cause: String?) =
    "Error while processing change log [$path]: ${cause?.uncapitalize()}"

private fun String.uncapitalize() = StringUtils.uncapitalizeAsProperty(this)

private fun createErrorPrefix(counter: Int, vararg properties: String?): String {
    return properties.filterNotNull().joinToString(".", "changeSet[$counter].")
}

private fun Regex.extractFirstGroup(message: String): String? {
    return find(message)
        ?.groupValues
        ?.get(1)
}
