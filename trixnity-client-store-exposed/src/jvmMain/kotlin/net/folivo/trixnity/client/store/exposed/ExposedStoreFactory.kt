package net.folivo.trixnity.client.store.exposed

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import javax.sql.DataSource

class ExposedStoreFactory(
    private val dataSource: DataSource,
    private val transactionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope,
    loggerFactory: LoggerFactory
) : StoreFactory {

    private val log = newLogger(loggerFactory)

    override suspend fun createStore(
        contentMappings: EventContentSerializerMappings,
        json: Json,
        loggerFactory: LoggerFactory
    ): Store {
        val exposedDatabase = Database.connect(dataSource)
        val liquibase =
            Liquibase(
                "db-changelog/main.yml",
                ClassLoaderResourceAccessor(),
                JdbcConnection(dataSource.connection)
            )

        withContext(transactionDispatcher) {
            log.info { "start database migration" }
            liquibase.update(Contexts(), LabelExpression())
            log.info { "finished database migration" }
        }

        log.debug { "Check if database matches schema from exposed. This does not detect superfluous tables or columns!" }
        val exposedDiff = newSuspendedTransaction(transactionDispatcher, exposedDatabase) {
            val tables = arrayOf(
                ExposedAccount,
                ExposedCrossSigningKeys,
                ExposedDeviceKeys,
                ExposedGlobalAccountData,
                ExposedInboundMegolmMessageIndex,
                ExposedInboundMegolmSession,
                ExposedKeyChainLink,
                ExposedKeyVerificationState,
                ExposedMedia,
                ExposedOlmAccount,
                ExposedOlmSession,
                ExposedOutboundMegolmSession,
                ExposedOutdatedKeys,
                ExposedRoomAccountData,
                ExposedRoomOutboxMessage,
                ExposedRoom,
                ExposedRoomState,
                ExposedRoomTimeline,
                ExposedRoomUser,
                ExposedUploadMedia,
            )
            SchemaUtils.statementsRequiredToActualizeScheme(*tables)
        }
        if (exposedDiff.isNotEmpty()) {
            log.debug { "diff from exposed: \n${exposedDiff.joinToString("\n")}" }
            throw ExposedMigrationCheckException
        }

        return ExposedStore(
            scope = scope,
            transactionDispatcher = transactionDispatcher,
            json = json,
            contentMappings = contentMappings,
            database = exposedDatabase
        )
    }
}