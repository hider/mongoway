package io.github.hider.mongoway

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import com.mongodb.event.ServerDescriptionChangedEvent
import com.mongodb.event.ServerListener
import org.bson.codecs.configuration.CodecRegistry
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
import java.util.concurrent.CountDownLatch

@Component
class MongoConnection(
    private val customCodecRegistry: CodecRegistry,
    private val buildProperties: BuildProperties,
    private val customConversions: MongoCustomConversions,
    private val sourceTypeMap: Map<Class<out ChangeAction>, String>,
) {
    fun useDatabase(
        connectionString: String,
        block: (db: MongoDatabase, repo: DatabaseChangelogRepository) -> Unit,
    ) {
        val changeSignal = CountDownLatch(1)
        var serverException: Throwable? = null
        val (databaseName, cs) = createConnectionString(connectionString)
        val clientSettings = MongoClientSettings.builder()
            .applyConnectionString(cs)
            .codecRegistry(customCodecRegistry)
            .applyToServerSettings { builder ->
                builder.addServerListener(object : ServerListener {
                    override fun serverDescriptionChanged(event: ServerDescriptionChangedEvent) {
                        if (event.newDescription.exception != null) {
                            serverException = event.newDescription.exception
                        }
                        changeSignal.countDown()
                    }
                })
            }
            .build()
        MongoClients.create(clientSettings).use { mongo ->
            // No explicit timeout handling here, as we rely on the server listener to notify us of any issues.
            changeSignal.await()
            val ex = serverException
            if (ex != null) {
                throw StartupException("Unable to connect to the MongoDB database. Ensure the specified server is running and accessible on your network. ${ex.message}: ${ex.cause?.message}.")
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
            val dbName = cs.database
            if (dbName == null) {
                throw StartupException("Database name is required in the connection string.")
            }
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
            "$javaName/$javaVersion ${buildProperties.name}/${buildProperties.version}".let {
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
