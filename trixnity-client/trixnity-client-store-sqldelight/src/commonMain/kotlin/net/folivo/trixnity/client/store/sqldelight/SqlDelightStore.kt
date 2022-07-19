package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RepositoryTransactionManager
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.coroutines.CoroutineContext

class SqlDelightStore(
    val db: Database,
    contentMappings: EventContentSerializerMappings,
    json: Json,
    dbContext: CoroutineContext,
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
                db.transactionWithResult {
                    callRunBlocking {
                        block()
                    }
                }
            }
    },
    accountRepository = SqlDelightAccountRepository(db.accountQueries, dbContext),
    outdatedKeysRepository = SqlDelightOutdatedDeviceKeysRepository(db.keysQueries, json, dbContext),
    deviceKeysRepository = SqlDelightDeviceKeysRepository(db.keysQueries, json, dbContext),
    crossSigningKeysRepository = SqlDelightCrossSigningKeysRepository(db.keysQueries, json, dbContext),
    keyVerificationStateRepository = SqlDelightKeyVerificationStateRepository(db.keysQueries, json, dbContext),
    keyChainLinkRepository = SqlDelightKeyChainLinkRepository(db.keysQueries, dbContext),
    secretsRepository = SqlDelightSecretsRepository(db.keysQueries, json, dbContext),
    secretKeyRequestRepository = SqlDelightSecretKeyRequestRepository(db.keysQueries, json, dbContext),
    olmAccountRepository = SqlDelightOlmAccountRepository(db.olmQueries, dbContext),
    olmSessionRepository = SqlDelightOlmSessionRepository(db.olmQueries, json, dbContext),
    inboundMegolmSessionRepository = SqlDelightInboundMegolmSessionRepository(db.olmQueries, json, dbContext),
    inboundMegolmMessageIndexRepository = SqlDelightInboundMegolmMessageIndexRepository(db.olmQueries, dbContext),
    outboundMegolmSessionRepository = SqlDelightOutboundMegolmSessionRepository(db.olmQueries, json, dbContext),
    roomRepository = SqlDelightRoomRepository(db.roomQueries, json, dbContext),
    roomUserRepository = SqlDelightRoomUserRepository(db.roomUserQueries, json, dbContext),
    roomStateRepository = SqlDelightRoomStateRepository(db.roomStateQueries, json, dbContext),
    timelineEventRepository = SqlDelightTimelineEventRepository(db.roomTimelineQueries, json, dbContext),
    timelineEventRelationRepository = SqlDelightTimelineEventRelationRepository(db.roomTimelineQueries, dbContext),
    roomOutboxMessageRepository = SqlDelightRoomOutboxMessageRepository(
        db.roomOutboxMessageQueries, json, contentMappings, dbContext
    ),
    mediaRepository = SqlDelightMediaRepository(db.mediaQueries, dbContext),
    uploadMediaRepository = SqlDelightUploadMediaRepository(db.mediaQueries, dbContext),
    globalAccountDataRepository = SqlDelightGlobalAccountDataRepository(db.globalAccountDataQueries, json, dbContext),
    roomAccountDataRepository = SqlDelightRoomAccountDataRepository(db.roomAccountDataQueries, json, dbContext)
)