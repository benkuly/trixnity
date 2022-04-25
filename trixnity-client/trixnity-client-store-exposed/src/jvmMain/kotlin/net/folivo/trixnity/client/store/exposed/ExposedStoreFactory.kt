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
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

private val log = KotlinLogging.logger {}

class ExposedStoreFactory(
    private val database: Database,
    private val transactionDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope,
) : StoreFactory {

    companion object {
        const val currentVersion: Long = 1L
    }

    override suspend fun createStore(
        contentMappings: EventContentSerializerMappings,
        json: Json,
    ): Store {
        log.debug { "create missing tables and columns" }
        newSuspendedTransaction(transactionDispatcher, database) {
            val allTables = arrayOf(
                ExposedDbVersion,
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
            withDataBaseLock {
                SchemaUtils.createMissingTablesAndColumns(*allTables)
                val currentDatabaseVersion = ExposedDbVersion.select { ExposedDbVersion.id.eq(0L) }.firstOrNull()
                    ?.let { it[ExposedDbVersion.version] } ?: 0
                when {
                    currentDatabaseVersion < 1 -> {
                        execInBatch(
                            listOf(
                                """ALTER TABLE key_verification_state DROP CONSTRAINT IF EXISTS pk_key_verification_state;""",
                                """ALTER TABLE key_verification_state DROP COLUMN IF EXISTS (user_id, device_id);""",
                                """ALTER TABLE key_verification_state ADD CONSTRAINT pk_key_verification_state PRIMARY KEY (key_id, key_algorithm);"""
                            )
                        )
                    }
                    else -> {}
                }
                ExposedDbVersion.replace {
                    it[ExposedDbVersion.id] = 0
                    it[version] = currentVersion
                }
            }
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