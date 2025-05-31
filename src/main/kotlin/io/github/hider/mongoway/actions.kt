package io.github.hider.mongoway

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.IndexOptions
import org.bson.Document
import org.bson.types.Binary
import org.bson.types.ObjectId
import org.springframework.core.io.Resource
import org.springframework.data.annotation.Transient
import org.springframework.data.mongodb.core.mapping.FieldName
import java.security.MessageDigest

/**
 * Represents an action that can be performed on a MongoDB collection.
 *
 * Nullable (optional) properties has no non-null default values, so they are not included in the JSON representation.
 */
sealed interface ChangeAction {
    fun validateOrThrow(context: ActionContext, changelogPath: Resource): Hash
    fun execute(collection: MongoCollection<Document>): ExecutionResult
}

sealed class WithSingleDocument : ChangeAction {
    abstract val document: Document?
    abstract val externalDocumentPath: String?
    abstract val relativeToChangelog: Boolean?
    @Transient
    protected var validatedDocument: Document? = null
        private set

    override fun validateOrThrow(context: ActionContext, changelogPath: Resource): Hash {
        if (document == null && externalDocumentPath == null) {
            throw ChangeValidationException("either change.document or change.externalDocumentPath must be provided")
        }
        if (document != null && externalDocumentPath != null) {
            throw ChangeValidationException("only the change.document or the change.externalDocumentPath can be specified (not both)")
        }
        if (relativeToChangelog != null && externalDocumentPath == null) {
            throw ChangeValidationException("change.relativeToChangelog is only valid when change.externalDocumentPath is provided")
        }
        validatedDocument = if (relativeToChangelog == true) {
            val externalDocument = changelogPath.createRelative(externalDocumentPath!!)
            context.toDocument(externalDocument)
        } else {
            document ?: context.toDocument(externalDocumentPath!!)
        }
        return validatedDocument!!.toHash()
    }
}

sealed class WithMultipleDocuments : ChangeAction {
    abstract val documents: List<Document>?
    abstract val externalDocumentsPath: String?
    abstract val relativeToChangelog: Boolean?

    @Transient
    protected var validatedDocuments: List<Document>? = null
        private set

    override fun validateOrThrow(context: ActionContext, changelogPath: Resource): Hash {
        if (documents == null && externalDocumentsPath == null) {
            throw ChangeValidationException("either change.documents or change.externalDocumentsPath must be provided")
        }
        if (documents != null && externalDocumentsPath != null) {
            throw ChangeValidationException("only the change.documents or the change.externalDocumentsPath can be specified (not both)")
        }
        if (relativeToChangelog != null && externalDocumentsPath == null) {
            throw ChangeValidationException("change.relativeToChangelog is only valid when change.externalDocumentsPath is provided")
        }
        validatedDocuments = if (relativeToChangelog == true) {
            val externalDocument = changelogPath.createRelative(externalDocumentsPath!!)
            context.toDocuments(externalDocument)
        } else {
            documents ?: context.toDocuments(externalDocumentsPath!!)
        }
        return validatedDocuments!!.toHash()
    }
}

data class InsertOne(
    override val document: Document?,
    override val externalDocumentPath: String?,
    override val relativeToChangelog: Boolean?,
) : WithSingleDocument() {

    override fun execute(collection: MongoCollection<Document>): ExecutionResult {
        val result = collection.insertOne(validatedDocument!!)
        val rollback = result.insertedId?.let {
            DeleteOne(
                Document(FieldName.ID.name, result.insertedId),
                failWithoutDelete = true,
            )
        }
        return ExecutionResult(rollback)
    }
}

data class InsertMany(
    override val documents: List<Document>?,
    override val externalDocumentsPath: String?,
    override val relativeToChangelog: Boolean?,
) : WithMultipleDocuments() {

    override fun validateOrThrow(
        context: ActionContext,
        changelogPath: Resource,
    ): Hash {
        val hash = super.validateOrThrow(context, changelogPath)
        if (validatedDocuments!!.isEmpty()) {
            throw ChangeValidationException("insertMany documents must not be empty")
        }
        return hash
    }

    override fun execute(collection: MongoCollection<Document>): ExecutionResult {
        collection.insertMany(validatedDocuments!!)
        return ExecutionResult(null)
    }
}

data class UpdateOne(
    override val document: Document?,
    override val externalDocumentPath: String?,
    val filter: Document,
    val failWithoutUpdate: Boolean?,
    override val relativeToChangelog: Boolean?,
) : WithSingleDocument() {

    override fun validateOrThrow(context: ActionContext, changelogPath: Resource): Hash {
        if (filter.isEmpty()) {
            throw ChangeValidationException("filter is required for updateOne action")
        }
        super.validateOrThrow(context, changelogPath)
        return listOf(validatedDocument!!, filter).toHash()
    }

    override fun execute(collection: MongoCollection<Document>): ExecutionResult {
        val originalDocument = collection.findOneAndUpdate(processFilter(filter), validatedDocument!!)
        if ((failWithoutUpdate == null || failWithoutUpdate) && originalDocument == null) {
            throw ChangeValidationException("no document matched the filter and change.failWithoutUpdate is true (default is true)")
        }
        val rollback = originalDocument?.let { document ->
            UpdateOne(
                document,
                externalDocumentPath = null,
                filter,
                null,
                null,
            )
        }
        return ExecutionResult(rollback)
    }
}

data class UpdateMany(
    override val document: Document?,
    override val externalDocumentPath: String?,
    val filter: Document,
    /**
     * Fail if no document matched the filter. Default is true.
     */
    val failWithoutUpdate: Boolean?,
    /**
     * The UpdateMany action will fail if the modified document count does not match this value.
     * Leave it null or set it to -1 disable this check.
     */
    val expectedModifiedCount: Long?,
    override val relativeToChangelog: Boolean? = null,
) : WithSingleDocument() {

    override fun validateOrThrow(context: ActionContext, changelogPath: Resource): Hash {
        if (filter.isEmpty()) {
            throw ChangeValidationException("filter is required for updateMany action")
        }
        super.validateOrThrow(context, changelogPath)
        return listOf(validatedDocument!!, filter).toHash()
    }

    override fun execute(collection: MongoCollection<Document>): ExecutionResult {
        val result = collection.updateMany(processFilter(filter), validatedDocument!!)
        if ((failWithoutUpdate == null || failWithoutUpdate) && result.modifiedCount == 0L) {
            throw ChangeValidationException("no document matched the filter and change.failWithoutUpdate is true (default is true)")
        }
        if (expectedModifiedCount != null && expectedModifiedCount != -1L && result.modifiedCount != expectedModifiedCount) {
            throw ChangeValidationException("expected modified count $expectedModifiedCount but actual modified count ${result.modifiedCount}")
        }
        return ExecutionResult(null)
    }
}

data class DeleteOne(
    /**
     * The filter to match the document to delete.
     * It must contain at least one field.
     */
    val filter: Document,
    /**
     * Fail if no document matched the filter. Default is true.
     */
    val failWithoutDelete: Boolean?,
) : ChangeAction {

    override fun validateOrThrow(context: ActionContext, changelogPath: Resource): Hash {
        if (filter.isEmpty()) {
            throw ChangeValidationException("filter is required for deleteOne action")
        }
        return filter.toHash()
    }

    override fun execute(collection: MongoCollection<Document>): ExecutionResult {
        val originalDocument = collection.findOneAndDelete(processFilter(filter))
        if ((failWithoutDelete == null || failWithoutDelete) && originalDocument == null) {
            throw ChangeValidationException("no document matched the filter and change.failWithoutDelete is true (default is true)")
        }
        val rollback = originalDocument?.let {
            InsertOne(
                originalDocument,
                null,
                null,
            )
        }
        return ExecutionResult(rollback)
    }
}

data class CreateIndex(
    val keys: Document,
    val options: Document?,
) : ChangeAction {

    @Transient
    private var indexOptions: IndexOptions? = null

    override fun validateOrThrow(context: ActionContext, changelogPath: Resource): Hash {
        if (keys.isEmpty()) {
            throw ChangeValidationException("keys is required for createIndex action")
        }
        if (options != null) {
            indexOptions = IndexOptions()
                .unique(options.getBoolean("unique", false))
                .name(options.getString("name"))
                .sparse(options.getBoolean("spare", false))
                .hidden(options.getBoolean("hidden", false))
            return listOf(keys, options).toHash()
        }
        return keys.toHash()
    }

    override fun execute(collection: MongoCollection<Document>): ExecutionResult {
        val name = if (indexOptions == null) {
            collection.createIndex(keys)
        } else {
            collection.createIndex(keys, indexOptions!!)
        }
        return ExecutionResult(DropIndex(name, null))
    }
}

data class DropIndex(
    val name: String?,
    val keys: Document?,
) : ChangeAction {

    override fun validateOrThrow(context: ActionContext, changelogPath: Resource): Hash {
        if (name == null) {
            if (keys == null) {
                throw ChangeValidationException("either change.name or change.keys must be provided")
            }
            if (keys.isEmpty()) {
                throw ChangeValidationException("change.keys must not be empty")
            }
            return keys.toHash()
        }
        if (keys == null) {
            return name.toByteArray().toHash()
        }
        throw ChangeValidationException("only the change.name or the change.name can be specified (not both)")
    }

    override fun execute(collection: MongoCollection<Document>): ExecutionResult {
        if (name != null) {
            collection.dropIndex(name)
            return ExecutionResult(null)
        }
        collection.dropIndex(keys!!)
        return ExecutionResult(CreateIndex(keys, null))
    }
}

data class ExecutionResult(
    val rollback: ChangeAction?,
)

private fun ByteArray.toHash(): Hash {
    val hashBytes = MessageDigest.getInstance("SHA-512").digest(this)
    return Hash(Binary(hashBytes))
}

private fun Document.toHash(): Hash {
    return toJson().toByteArray().toHash()
}

private fun List<Document>.toHash(): Hash {
    return this.joinToString(prefix = "[", postfix = "]") {
        it.toJson()
    }
        .toByteArray()
        .toHash()
}

private fun processFilter(filter: Document): Document {
    val oid = filter[FieldName.ID.name]
    if (oid is String && ObjectId.isValid(oid)) {
        val copy = Document(filter)
        copy[FieldName.ID.name] = ObjectId(oid)
        return copy
    }
    return filter
}
