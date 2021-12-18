package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RepositoryTransactionManager
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import kotlin.coroutines.CoroutineContext

class SqlDelightStore(
    val database: Database,
    contentMappings: EventContentSerializerMappings,
    json: Json,
    databaseCoroutineContext: CoroutineContext,
    private val blockingTransactionCoroutineContext: CoroutineContext,
    scope: CoroutineScope
) : Store(
    scope = scope,
    contentMappings = contentMappings,
    rtm = object : RepositoryTransactionManager {
        /**
         * This implementation is very hacky. SqlDelight only allows transactions of thread-blocking code.
         * Because we don't do super heavy stuff within a transaction this should not affect the performance very much.
         */
        override suspend fun <T> transaction(block: suspend () -> T): T =
            callRunBlocking(blockingTransactionCoroutineContext) {
                database.transactionWithResult {
                    callRunBlocking {
                        block()
                    }
                }
            }
    },
    accountRepository = SqlDelightAccountRepository(database.accountQueries, databaseCoroutineContext),
    outdatedKeysRepository = SqlDelightOutdatedDeviceKeysRepository(
        database.keysQueries, json, databaseCoroutineContext
    ),
    deviceKeysRepository = SqlDelightDeviceKeysRepository(
        database.keysQueries, json, databaseCoroutineContext
    ),
    crossSigningKeysRepository = SqlDelightCrossSigningKeysRepository(
        database.keysQueries, json, databaseCoroutineContext
    ),
    keyVerificationStateRepository = SqlDelightKeyVerificationStateRepository(
        database.keysQueries, json, databaseCoroutineContext
    ),
    keyChainLinkRepository = SqlDelightKeyChainLinkRepository(database.keysQueries, databaseCoroutineContext),
    olmAccountRepository = SqlDelightOlmAccountRepository(database.olmQueries, databaseCoroutineContext),
    olmSessionRepository = SqlDelightOlmSessionRepository(database.olmQueries, json, databaseCoroutineContext),
    inboundMegolmSessionRepository = SqlDelightInboundMegolmSessionRepository(
        database.olmQueries, databaseCoroutineContext
    ),
    inboundMegolmMessageIndexRepository = SqlDelightInboundMegolmMessageIndexRepository(
        database.olmQueries, databaseCoroutineContext
    ),
    outboundMegolmSessionRepository = SqlDelightOutboundMegolmSessionRepository(
        database.olmQueries, json, databaseCoroutineContext
    ),
    roomRepository = SqlDelightRoomRepository(database.roomQueries, json, databaseCoroutineContext),
    roomUserRepository = SqlDelightRoomUserRepository(database.roomUserQueries, json, databaseCoroutineContext),
    roomStateRepository = SqlDelightRoomStateRepository(
        database.roomStateQueries,
        json,
        databaseCoroutineContext
    ),
    roomTimelineRepository = SqlDelightRoomTimelineRepository(
        database.roomTimelineQueries, json, databaseCoroutineContext
    ),
    roomOutboxMessageRepository = SqlDelightRoomOutboxMessageRepository(
        database.roomOutboxMessageQueries, json, contentMappings, databaseCoroutineContext
    ),
    mediaRepository = SqlDelightMediaRepository(database.mediaQueries, databaseCoroutineContext),
    uploadMediaRepository = SqlDelightUploadMediaRepository(database.mediaQueries, databaseCoroutineContext),
    globalAccountDataRepository = SqlDelightGlobalAccountDataRepository(
        database.globalAccountDataQueries,
        json,
        databaseCoroutineContext
    ),
    roomAccountDataRepository = SqlDelightRoomAccountDataRepository(
        database.roomAccountDataQueries,
        json,
        databaseCoroutineContext
    )
)