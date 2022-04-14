package net.folivo.trixnity.client.store.exposed

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

private val log = KotlinLogging.logger {}

class ExposedStoreFactory(
    private val database: Database,
    private val transactionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope,
) : StoreFactory {


    override suspend fun createStore(
        contentMappings: EventContentSerializerMappings,
        json: Json,
    ): Store {
        log.debug { "create missing tables and columns" }
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
                ExposedSecrets,
                ExposedSecretKeyRequest,
                ExposedMedia,
                ExposedOlmAccount,
                ExposedOlmSession,
                ExposedOutboundMegolmSession,
                ExposedOutdatedKeys,
                ExposedRoomAccountData,
                ExposedRoomOutboxMessage,
                ExposedRoom,
                ExposedRoomState,
                ExposedRoomTimelineEvent,
                ExposedRoomUser,
                ExposedUploadMedia,
            )
            SchemaUtils.createMissingTablesAndColumns(*tables)
        }
        log.debug { "finished create missing tables and columns" }

        return ExposedStore(
            scope = scope,
            transactionDispatcher = transactionDispatcher,
            json = json,
            contentMappings = contentMappings,
            database = database
        )
    }
}