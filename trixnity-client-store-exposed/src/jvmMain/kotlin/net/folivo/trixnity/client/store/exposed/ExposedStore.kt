package net.folivo.trixnity.client.store.exposed

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RepositoryTransactionManager
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedStore(
    val database: Database,
    contentMappings: EventContentSerializerMappings,
    json: Json,
    private val transactionDispatcher: CoroutineDispatcher,
    scope: CoroutineScope
) : Store(
    scope = scope,
    contentMappings = contentMappings,
    rtm = object : RepositoryTransactionManager {
        override suspend fun <T> transaction(block: suspend () -> T): T =
            newSuspendedTransaction(transactionDispatcher, database) { block() }
    },
    accountRepository = ExposedAccountRepository(),
    outdatedKeysRepository = ExposedOutdatedKeysRepository(json),
    deviceKeysRepository = ExposedDeviceKeysRepository(json),
    crossSigningKeysRepository = ExposedCrossSigningKeysRepository(json),
    keyVerificationStateRepository = ExposedKeyVerificationStateRepository(json),
    keyChainLinkRepository = ExposedKeyChainLinkRepository(),
    secretsRepository = ExposedSecretsRepository(json),
    secretKeyRequestRepository = ExposedSecretKeyRequestRepository(json),
    olmAccountRepository = ExposedOlmAccountRepository(),
    olmSessionRepository = ExposedOlmSessionRepository(json),
    inboundMegolmSessionRepository = ExposedInboundMegolmSessionRepository(json),
    inboundMegolmMessageIndexRepository = ExposedInboundMegolmMessageIndexRepository(),
    outboundMegolmSessionRepository = ExposedOutboundMegolmSessionRepository(json),
    roomRepository = ExposedRoomRepository(json),
    roomUserRepository = ExposedRoomUserRepository(json),
    roomStateRepository = ExposedRoomStateRepository(json),
    roomTimelineEventRepository = ExposedRoomTimelineEventRepository(json),
    roomOutboxMessageRepository = ExposedRoomOutboxMessageRepository(json, contentMappings),
    mediaRepository = ExposedMediaRepository(),
    uploadMediaRepository = ExposedUploadMediaRepository(),
    globalAccountDataRepository = ExposedGlobalAccountDataRepository(json),
    roomAccountDataRepository = ExposedRoomAccountDataRepository(json),
)