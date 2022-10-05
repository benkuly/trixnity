package net.folivo.trixnity.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.configurePortableMockEngine
import net.folivo.trixnity.testutils.mockEngineFactory

val simpleRoom = Room(RoomId("room", "server"), lastEventId = EventId("\$event"))

object NoopRepositoryTransactionManager : RepositoryTransactionManager {
    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}

fun mockMatrixClientServerApiClient(json: Json): Pair<MatrixClientServerApiClientImpl, PortableMockEngineConfig> {
    val config = PortableMockEngineConfig()
    val api = MatrixClientServerApiClientImpl(
        json = json,
        httpClientFactory = mockEngineFactory { configurePortableMockEngine(config) }
    )
    return api to config
}

suspend fun getInMemoryAccountStore(scope: CoroutineScope) = AccountStore(
    InMemoryAccountRepository(),
    NoopRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryRoomAccountDataStore(scope: CoroutineScope) = RoomAccountDataStore(
    InMemoryRoomAccountDataRepository(),
    NoopRepositoryTransactionManager,
    DefaultEventContentSerializerMappings,
    scope
).apply { init() }

suspend fun getInMemoryGlobalAccountDataStore(scope: CoroutineScope) = GlobalAccountDataStore(
    InMemoryGlobalAccountDataRepository(),
    NoopRepositoryTransactionManager,
    DefaultEventContentSerializerMappings,
    scope
).apply { init() }

suspend fun getInMemoryOlmStore(scope: CoroutineScope) = OlmCryptoStore(
    InMemoryOlmAccountRepository(),
    InMemoryOlmSessionRepository(),
    InMemoryInboundMegolmSessionRepository(),
    InMemoryInboundMegolmMessageIndexRepository(),
    InMemoryOutboundMegolmSessionRepository(),
    NoopRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryKeyStore(scope: CoroutineScope) = KeyStore(
    InMemoryOutdatedKeysRepository(),
    InMemoryDeviceKeysRepository(),
    InMemoryCrossSigningKeysRepository(),
    InMemoryKeyVerificationStateRepository(),
    InMemoryKeyChainLinkRepository(),
    InMemorySecretsRepository(),
    InMemorySecretKeyRequestRepository(),
    NoopRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryRoomStore(scope: CoroutineScope) = RoomStore(
    InMemoryRoomRepository(),
    NoopRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryRoomTimelineStore(scope: CoroutineScope) = RoomTimelineStore(
    InMemoryTimelineEventRepository(),
    InMemoryTimelineEventRelationRepository(),
    NoopRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryRoomStateStore(scope: CoroutineScope) = RoomStateStore(
    InMemoryRoomStateRepository(),
    NoopRepositoryTransactionManager,
    DefaultEventContentSerializerMappings,
    scope
).apply { init() }

suspend fun getInMemoryRoomUserStore(scope: CoroutineScope) = RoomUserStore(
    InMemoryRoomUserRepository(),
    NoopRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryMediaStore(scope: CoroutineScope) = MediaStore(
    InMemoryMediaRepository(),
    InMemoryUploadMediaRepository(),
    NoopRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryRoomOutboxMessageStore(scope: CoroutineScope) = RoomOutboxMessageStore(
    InMemoryRoomOutboxMessageRepository(),
    NoopRepositoryTransactionManager,
    scope
).apply { init() }