package net.folivo.trixnity.client.store.exposed

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class ExposedStoreFactory(
    private val database: Database,
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
        log.info { "create missing tables and columns" }
        newSuspendedTransaction(transactionDispatcher, database) {
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
            SchemaUtils.createMissingTablesAndColumns(*tables)
        }
        log.info { "finished create missing tables and columns" }

        return ExposedStore(
            scope = scope,
            transactionDispatcher = transactionDispatcher,
            json = json,
            contentMappings = contentMappings,
            database = database
        )
    }
}