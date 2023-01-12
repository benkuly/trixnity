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
    NoOpRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryRoomAccountDataStore(scope: CoroutineScope) = RoomAccountDataStore(
    InMemoryRoomAccountDataRepository(),
    NoOpRepositoryTransactionManager,
    DefaultEventContentSerializerMappings,
    scope
).apply { init() }

suspend fun getInMemoryGlobalAccountDataStore(scope: CoroutineScope) = GlobalAccountDataStore(
    InMemoryGlobalAccountDataRepository(),
    NoOpRepositoryTransactionManager,
    DefaultEventContentSerializerMappings,
    scope
).apply { init() }

suspend fun getInMemoryOlmStore(scope: CoroutineScope) = OlmCryptoStore(
    InMemoryOlmAccountRepository(),
    InMemoryOlmForgetFallbackKeyAfterRepository(),
    InMemoryOlmSessionRepository(),
    InMemoryInboundMegolmSessionRepository(),
    InMemoryInboundMegolmMessageIndexRepository(),
    InMemoryOutboundMegolmSessionRepository(),
    NoOpRepositoryTransactionManager,
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
    NoOpRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryRoomStore(scope: CoroutineScope) = RoomStore(
    InMemoryRoomRepository(),
    NoOpRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryRoomTimelineStore(scope: CoroutineScope) = RoomTimelineStore(
    InMemoryTimelineEventRepository(),
    InMemoryTimelineEventRelationRepository(),
    NoOpRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryRoomStateStore(scope: CoroutineScope) = RoomStateStore(
    InMemoryRoomStateRepository(),
    NoOpRepositoryTransactionManager,
    DefaultEventContentSerializerMappings,
    scope
).apply { init() }

suspend fun getInMemoryRoomUserStore(scope: CoroutineScope) = RoomUserStore(
    InMemoryRoomUserRepository(),
    NoOpRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryMediaCacheMapping(scope: CoroutineScope) = MediaCacheMappingStore(
    InMemoryMediaCacheMappingRepository(),
    NoOpRepositoryTransactionManager,
    scope
).apply { init() }

suspend fun getInMemoryRoomOutboxMessageStore(scope: CoroutineScope) = RoomOutboxMessageStore(
    InMemoryRoomOutboxMessageRepository(),
    NoOpRepositoryTransactionManager,
    scope
).apply { init() }