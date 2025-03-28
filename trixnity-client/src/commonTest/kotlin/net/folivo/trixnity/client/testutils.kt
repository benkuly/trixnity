package net.folivo.trixnity.client

import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import net.folivo.trixnity.clientserverapi.client.SyncBatchTokenStore
import net.folivo.trixnity.clientserverapi.model.media.GetMediaConfig
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
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
import net.folivo.trixnity.testutils.mockEngineWithEndpoints

fun String.trimToFlatJson() =
    this.trimIndent().lines().filter { it.isNotEmpty() }.joinToString("") { it.replace(": ", ":").trim() }

val simpleRoom = Room(RoomId("room", "server"), lastEventId = EventId("\$event"))
val simpleUserInfo =
    UserInfo(UserId("me", "server"), "myDevice", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, ""))

fun mockMatrixClientServerApiClient(
    json: Json = createMatrixEventJson(),
    contentMappings: EventContentSerializerMappings = createDefaultEventContentSerializerMappings(),
    syncBatchTokenStore: SyncBatchTokenStore = SyncBatchTokenStore.inMemory(),
): Pair<MatrixClientServerApiClientImpl, PortableMockEngineConfig> {
    val config = PortableMockEngineConfig()
    val api = MatrixClientServerApiClientImpl(
        json = json,
        httpClientEngine = mockEngineWithEndpoints(json, contentMappings, portableConfig = config),
        syncBatchTokenStore = syncBatchTokenStore
    )
    return api to config
}

suspend fun getInMemoryAccountStore(scope: CoroutineScope) = AccountStore(
    InMemoryAccountRepository(),
    RepositoryTransactionManagerMock(),
    ObservableCacheStatisticCollector(),
    scope,
    Clock.System,
).apply {
    init(scope)
    updateAccount {
        Account(
            olmPickleKey = "",
            baseUrl = "",
            userId = UserId("user", "server"),
            deviceId = "",
            accessToken = null,
            refreshToken = null,
            syncBatchToken = null,
            filterId = null,
            backgroundFilterId = null,
            displayName = null,
            avatarUrl = null,
            isLocked = false
        )
    }
}

suspend fun getInMemoryServerDataStore(scope: CoroutineScope) = ServerDataStore(
    InMemoryServerDataRepository().also {
        it.save(
            1,
            ServerData(GetVersions.Response(listOf("v1.11"), mapOf()), GetMediaConfig.Response(10_000))
        )
    },
    RepositoryTransactionManagerMock(),
    ObservableCacheStatisticCollector(),
    scope,
    Clock.System,
).apply { init(scope) }

suspend fun getInMemoryRoomAccountDataStore(scope: CoroutineScope) = RoomAccountDataStore(
    InMemoryRoomAccountDataRepository(),
    RepositoryTransactionManagerMock(),
    DefaultEventContentSerializerMappings,
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    scope,
    Clock.System,
).apply { init(scope) }

suspend fun getInMemoryGlobalAccountDataStore(scope: CoroutineScope) = GlobalAccountDataStore(
    InMemoryGlobalAccountDataRepository(),
    RepositoryTransactionManagerMock(),
    DefaultEventContentSerializerMappings,
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    scope,
    Clock.System,
).apply { init(scope) }

suspend fun getInMemoryOlmStore(scope: CoroutineScope) = OlmCryptoStore(
    InMemoryOlmAccountRepository(),
    InMemoryOlmForgetFallbackKeyAfterRepository(),
    InMemoryOlmSessionRepository(),
    InMemoryInboundMegolmSessionRepository(),
    InMemoryInboundMegolmMessageIndexRepository(),
    InMemoryOutboundMegolmSessionRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    scope,
    Clock.System,
).apply { init(scope) }

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
    ObservableCacheStatisticCollector(),
    scope,
    Clock.System,
).apply { init(scope) }

suspend fun getInMemoryRoomStore(scope: CoroutineScope) = RoomStore(
    InMemoryRoomRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    scope,
    Clock.System,
).apply { init(scope) }

suspend fun getInMemoryRoomTimelineStore(scope: CoroutineScope) = RoomTimelineStore(
    InMemoryTimelineEventRepository(),
    InMemoryTimelineEventRelationRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    scope,
    Clock.System,
).apply { init(scope) }

suspend fun getInMemoryRoomStateStore(scope: CoroutineScope) = RoomStateStore(
    InMemoryRoomStateRepository(),
    RepositoryTransactionManagerMock(),
    DefaultEventContentSerializerMappings,
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    scope,
    Clock.System,
).apply { init(scope) }

suspend fun getInMemoryRoomUserStore(scope: CoroutineScope) = RoomUserStore(
    InMemoryRoomUserRepository(),
    InMemoryRoomUserReceiptsRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    scope,
    Clock.System,
).apply { init(scope) }

suspend fun getInMemoryMediaCacheMapping(scope: CoroutineScope) = MediaCacheMappingStore(
    InMemoryMediaCacheMappingRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    scope,
    Clock.System,
).apply { init(scope) }

suspend fun getInMemoryRoomOutboxMessageStore(scope: CoroutineScope) = RoomOutboxMessageStore(
    InMemoryRoomOutboxMessageRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    scope,
    Clock.System,
).apply { init(scope) }

suspend fun ShouldSpecContainerScope.clearOutdatedKeys(keyStoreBuilder: () -> KeyStore) {
    lateinit var coroutineScope: CoroutineScope
    beforeTest {
        val keyStore = keyStoreBuilder()
        coroutineScope = CoroutineScope(Dispatchers.Default)
        coroutineScope.launch {
            keyStore.getOutdatedKeysFlow().filter { it.isNotEmpty() }.collect {
                keyStore.updateOutdatedKeys { setOf() }
            }
        }
    }
    afterTest {
        coroutineScope.cancel()
    }
}