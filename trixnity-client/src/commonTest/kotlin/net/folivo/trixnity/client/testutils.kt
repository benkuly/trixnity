package net.folivo.trixnity.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createDefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.mockEngineFactoryWithEndpoints

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }

val simpleRoom = Room(RoomId("room", "server"), lastEventId = EventId("\$event"))
val simpleUserInfo =
    UserInfo(UserId("me", "server"), "myDevice", Key.Ed25519Key(value = ""), Key.Curve25519Key(value = ""))

fun mockMatrixClientServerApiClient(
    json: Json = createMatrixEventJson(),
    contentMappings: EventContentSerializerMappings = createDefaultEventContentSerializerMappings(),
): Pair<MatrixClientServerApiClientImpl, PortableMockEngineConfig> {
    val config = PortableMockEngineConfig()
    val api = MatrixClientServerApiClientImpl(
        json = json,
        httpClientFactory = mockEngineFactoryWithEndpoints(json, contentMappings, portableConfig = config)
    )
    return api to config
}

suspend fun getInMemoryAccountStore(scope: CoroutineScope) = AccountStore(
    InMemoryAccountRepository(),
    RepositoryTransactionManagerMock(),
    scope
).apply { init() }

suspend fun getInMemoryRoomAccountDataStore(scope: CoroutineScope) = RoomAccountDataStore(
    InMemoryRoomAccountDataRepository(),
    RepositoryTransactionManagerMock(),
    DefaultEventContentSerializerMappings,
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryGlobalAccountDataStore(scope: CoroutineScope) = GlobalAccountDataStore(
    InMemoryGlobalAccountDataRepository(),
    RepositoryTransactionManagerMock(),
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
    RepositoryTransactionManagerMock(),
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
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryRoomStore(scope: CoroutineScope) = RoomStore(
    InMemoryRoomRepository(),
    RepositoryTransactionManagerMock(),
    scope
).apply { init() }

suspend fun getInMemoryRoomTimelineStore(scope: CoroutineScope) = RoomTimelineStore(
    InMemoryTimelineEventRepository(),
    InMemoryTimelineEventRelationRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryRoomStateStore(scope: CoroutineScope) = RoomStateStore(
    InMemoryRoomStateRepository(),
    RepositoryTransactionManagerMock(),
    DefaultEventContentSerializerMappings,
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryRoomUserStore(scope: CoroutineScope) = RoomUserStore(
    InMemoryRoomUserRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryMediaCacheMapping(scope: CoroutineScope) = MediaCacheMappingStore(
    InMemoryMediaCacheMappingRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    scope
).apply { init() }

suspend fun getInMemoryRoomOutboxMessageStore(scope: CoroutineScope) = RoomOutboxMessageStore(
    InMemoryRoomOutboxMessageRepository(),
    RepositoryTransactionManagerMock(),
    scope
).apply { init() }