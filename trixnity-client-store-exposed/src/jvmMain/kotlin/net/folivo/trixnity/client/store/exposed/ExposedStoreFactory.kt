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
import kotlin.coroutines.CoroutineContext

class ExposedStoreFactory(
    private val database: Database,
    private val transactionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope
) : StoreFactory {

    override suspend fun createStore(
        contentMappings: EventContentSerializerMappings,
        json: Json,
        storeCoroutineContext: CoroutineContext,
        loggerFactory: LoggerFactory
    ): Store {
        // TODO migration
        newSuspendedTransaction(transactionDispatcher, database) {
            SchemaUtils.create(
                ExposedAccount,
                ExposedCrossSigningKeys,
                ExposedDeviceKeys,
                ExposedGlobalAccountData,
                ExposedInboundMegolmMessageIndex,
                ExposedInboundMegolmSession,
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
                ExposedUploadMedia
            )
        }

        return ExposedStore(
            scope = scope,
            transactionDispatcher = transactionDispatcher,
            json = json,
            contentMappings = contentMappings,
            database = database
        )
    }
}