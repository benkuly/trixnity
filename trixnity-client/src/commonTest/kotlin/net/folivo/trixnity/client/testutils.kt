package net.folivo.trixnity.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.mocks.TransactionManagerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.configurePortableMockEngine
import net.folivo.trixnity.testutils.mockEngineFactory

val simpleRoom = Room(RoomId("room", "server"), lastEventId = EventId("\$event"))
val simpleUserInfo =
    UserInfo(UserId("me", "server"), "myDevice", Key.Ed25519Key(value = ""), Key.Curve25519Key(value = ""))

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
    TransactionManagerMock(),
    scope
).apply { init() }

suspend fun getInMemoryRoomAccountDataStore(scope: CoroutineScope) = RoomAccountDataStore(
    InMemoryRoomAccountDataRepository(),
    TransactionManagerMock(),
    DefaultEventContentSerializerMappings,
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryGlobalAccountDataStore(scope: CoroutineScope) = GlobalAccountDataStore(
    InMemoryGlobalAccountDataRepository(),
    TransactionManagerMock(),
    DefaultEventContentSerializerMappings,
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryOlmStore(scope: CoroutineScope) = OlmCryptoStore(
    InMemoryOlmAccountRepository(),
    InMemoryOlmForgetFallbackKeyAfterRepository(),
    InMemoryOlmSessionRepository(),
    InMemoryInboundMegolmSessionRepository(),
    InMemoryInboundMegolmMessageIndexRepository(),
    InMemoryOutboundMegolmSessionRepository(),
    TransactionManagerMock(),
    MatrixClientConfiguration(),
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
    InMemoryRoomKeyRequestRepository(),
    TransactionManagerMock(),
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryRoomStore(scope: CoroutineScope) = RoomStore(
    InMemoryRoomRepository(),
    TransactionManagerMock(),
    scope
).apply { init() }

suspend fun getInMemoryRoomTimelineStore(scope: CoroutineScope) = RoomTimelineStore(
    InMemoryTimelineEventRepository(),
    InMemoryTimelineEventRelationRepository(),
    TransactionManagerMock(),
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryRoomStateStore(scope: CoroutineScope) = RoomStateStore(
    InMemoryRoomStateRepository(),
    TransactionManagerMock(),
    DefaultEventContentSerializerMappings,
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryRoomUserStore(scope: CoroutineScope) = RoomUserStore(
    InMemoryRoomUserRepository(),
    TransactionManagerMock(),
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryMediaCacheMapping(scope: CoroutineScope) = MediaCacheMappingStore(
    InMemoryMediaCacheMappingRepository(),
    TransactionManagerMock(),
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryRoomOutboxMessageStore(scope: CoroutineScope) = RoomOutboxMessageStore(
    InMemoryRoomOutboxMessageRepository(),
    TransactionManagerMock(),
    scope
).apply { init() }