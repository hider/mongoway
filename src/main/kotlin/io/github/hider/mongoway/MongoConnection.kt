package io.github.hider.mongoway

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import io.github.hider.mongoway.errors.StartupException
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.info.BuildProperties
import org.springframework.data.convert.ConfigurableTypeInformationMapper
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.concurrent.schedule


@Component
class MongoConnection(
    private val customCodecRegistry: CodecRegistry,
    private val buildProperties: BuildProperties,
    private val customConversions: MongoCustomConversions,
    private val sourceTypeMap: Map<Class<out ChangeAction>, String>,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun useDatabase(
        connectionString: String,
        block: (db: MongoDatabase, repo: DatabaseChangelogRepository) -> Unit,
    ) {
        val (databaseName, cs) = createConnectionString(connectionString)
        val clientSettings = MongoClientSettings.builder()
            .applyConnectionString(cs)
            .codecRegistry(customCodecRegistry)
            .build()

        val timer = Timer(true)
        val warnTask = timer.schedule(2000L) {
            log.warn("Connecting to MongoDB is taking longer than expected. Please verify your connection string and network status...")
        }

        MongoClients.create(clientSettings).use { mongo ->
            try {
                mongo.getDatabase("admin").runCommand(Document("ping", 1))
            } catch (e: Exception) {
                throw StartupException("Unable to connect to the MongoDB database. Ensure the specified server is running and accessible on your network. ${e.message}", e)
            } finally {
                warnTask.cancel()
                timer.cancel()
            }

            val databaseFactory = SimpleMongoClientDatabaseFactory(mongo, databaseName)
            val converter = createMongoConverter(databaseFactory)
            val template = MongoTemplate(databaseFactory, converter)
            val repositoryFactory = MongoRepositoryFactory(template)
            val repo = repositoryFactory.getRepository(DatabaseChangelogRepository::class.java)
            val db = databaseFactory.getMongoDatabase(databaseName)
            block(db, repo)
        }
    }

    private fun createMongoConverter(databaseFactory: MongoDatabaseFactory): MappingMongoConverter {
        val dbRefResolver = DefaultDbRefResolver(databaseFactory)

        val mappingContext = MongoMappingContext().apply {
            setSimpleTypeHolder(customConversions.simpleTypeHolder)
            afterPropertiesSet()
        }

        val mapper = ConfigurableTypeInformationMapper(sourceTypeMap)
        return MappingMongoConverter(dbRefResolver, mappingContext).apply {
            customConversions = this@MongoConnection.customConversions
            setCodecRegistryProvider(databaseFactory)
            setTypeMapper(DefaultMongoTypeMapper("action", listOf(mapper)))
            afterPropertiesSet()
        }
    }

    private fun createConnectionString(connectionString: String): Pair<String, ConnectionString> {
        return if (connectionString.contains("://")) {
            val cs = ConnectionString(connectionString)
            val dbName = cs.database ?: throw StartupException("Database name is required in the connection string.")
            val client = if (cs.applicationName == null) {
                connectionStringWithCustomAppName(connectionString)
            } else {
                cs
            }
            Pair(dbName, client)
        } else {
            Pair(connectionString, connectionStringWithCustomAppName("mongodb://localhost"))
        }
    }

    private fun connectionStringWithCustomAppName(connectionStringBase: String): ConnectionString {
        val javaName = System.getProperty("java.vm.name").substringBefore(" ")
        val javaVersion = System.getProperty("java.vm.version")
        val appName =
            "$javaName/$javaVersion MongoWay/${buildProperties.version}".let {
                URLEncoder.encode(it, StandardCharsets.UTF_8)
            }
        val csUri = URI.create(connectionStringBase)
        val separator = if (csUri.query == null) {
            "?"
        } else if (csUri.query.isNotEmpty()) {
            "&"
        } else {
            ""
        }
        return ConnectionString(connectionStringBase + separator + "appName=$appName")
    }
}
