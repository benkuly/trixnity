package de.connect2x.trixnity.client

import de.connect2x.trixnity.client.mocks.RepositoryTransactionManagerMock
import de.connect2x.trixnity.client.store.Account
import de.connect2x.trixnity.client.store.AccountStore
import de.connect2x.trixnity.client.store.GlobalAccountDataStore
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.store.MediaCacheMappingStore
import de.connect2x.trixnity.client.store.NotificationStore
import de.connect2x.trixnity.client.store.OlmCryptoStore
import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.RoomAccountDataStore
import de.connect2x.trixnity.client.store.RoomOutboxMessageStore
import de.connect2x.trixnity.client.store.RoomStateStore
import de.connect2x.trixnity.client.store.RoomStore
import de.connect2x.trixnity.client.store.RoomTimelineStore
import de.connect2x.trixnity.client.store.RoomUserStore
import de.connect2x.trixnity.client.store.ServerData
import de.connect2x.trixnity.client.store.ServerDataStore
import de.connect2x.trixnity.client.store.StickyEventStore
import de.connect2x.trixnity.client.store.UserPresenceStore
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.InMemoryAccountRepository
import de.connect2x.trixnity.client.store.repository.InMemoryCrossSigningKeysRepository
import de.connect2x.trixnity.client.store.repository.InMemoryDeviceKeysRepository
import de.connect2x.trixnity.client.store.repository.InMemoryGlobalAccountDataRepository
import de.connect2x.trixnity.client.store.repository.InMemoryInboundMegolmMessageIndexRepository
import de.connect2x.trixnity.client.store.repository.InMemoryInboundMegolmSessionRepository
import de.connect2x.trixnity.client.store.repository.InMemoryKeyChainLinkRepository
import de.connect2x.trixnity.client.store.repository.InMemoryKeyVerificationStateRepository
import de.connect2x.trixnity.client.store.repository.InMemoryMediaCacheMappingRepository
import de.connect2x.trixnity.client.store.repository.InMemoryNotificationRepository
import de.connect2x.trixnity.client.store.repository.InMemoryNotificationStateRepository
import de.connect2x.trixnity.client.store.repository.InMemoryNotificationUpdateRepository
import de.connect2x.trixnity.client.store.repository.InMemoryOlmAccountRepository
import de.connect2x.trixnity.client.store.repository.InMemoryOlmForgetFallbackKeyAfterRepository
import de.connect2x.trixnity.client.store.repository.InMemoryOlmSessionRepository
import de.connect2x.trixnity.client.store.repository.InMemoryOutboundMegolmSessionRepository
import de.connect2x.trixnity.client.store.repository.InMemoryOutdatedKeysRepository
import de.connect2x.trixnity.client.store.repository.InMemoryRoomAccountDataRepository
import de.connect2x.trixnity.client.store.repository.InMemoryRoomKeyRequestRepository
import de.connect2x.trixnity.client.store.repository.InMemoryRoomOutboxMessageRepository
import de.connect2x.trixnity.client.store.repository.InMemoryRoomRepository
import de.connect2x.trixnity.client.store.repository.InMemoryRoomStateRepository
import de.connect2x.trixnity.client.store.repository.InMemoryRoomUserReceiptsRepository
import de.connect2x.trixnity.client.store.repository.InMemoryRoomUserRepository
import de.connect2x.trixnity.client.store.repository.InMemorySecretKeyRequestRepository
import de.connect2x.trixnity.client.store.repository.InMemorySecretsRepository
import de.connect2x.trixnity.client.store.repository.InMemoryServerDataRepository
import de.connect2x.trixnity.client.store.repository.InMemoryStickyEventRepository
import de.connect2x.trixnity.client.store.repository.InMemoryTimelineEventRelationRepository
import de.connect2x.trixnity.client.store.repository.InMemoryTimelineEventRepository
import de.connect2x.trixnity.client.store.repository.InMemoryUserPresenceRepository
import de.connect2x.trixnity.client.store.repository.NoOpRepositoryTransactionManager
import de.connect2x.trixnity.clientserverapi.client.ClassicMatrixClientAuthProvider
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderData
import de.connect2x.trixnity.clientserverapi.client.MatrixClientAuthProviderDataStore
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClientImpl
import de.connect2x.trixnity.clientserverapi.client.SyncBatchTokenStore
import de.connect2x.trixnity.clientserverapi.client.classic
import de.connect2x.trixnity.clientserverapi.model.media.GetMediaConfig
import de.connect2x.trixnity.clientserverapi.model.server.GetVersions
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.scopedMockEngineWithEndpoints
import io.kotest.assertions.nondeterministic.continuallyConfig
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.common.KotestInternal
import io.kotest.common.NonDeterministicTestVirtualTimeEnabled
import io.ktor.http.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

fun String.trimToFlatJson() =
    this.trimIndent().lines().filter { it.isNotEmpty() }.joinToString("") { it.replace(": ", ":").trim() }

val simpleRoom = Room(RoomId("!room:server"), lastEventId = EventId("\$event"))
val simpleUserInfo =
    UserInfo(UserId("me", "server"), "myDevice", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, ""))

fun TrixnityBaseTest.mockMatrixClientServerApiClient(
    config: PortableMockEngineConfig = PortableMockEngineConfig(),
    json: Json = createMatrixEventJson(),
    contentMappings: EventContentSerializerMappings = EventContentSerializerMappings.default,
    syncBatchTokenStore: SyncBatchTokenStore = SyncBatchTokenStore.inMemory(),
): MatrixClientServerApiClientImpl =
    MatrixClientServerApiClientImpl(
        json = json,
        httpClientEngine = testScope.backgroundScope.scopedMockEngineWithEndpoints(
            json,
            contentMappings,
            portableConfig = config
        ),
        authProvider = ClassicMatrixClientAuthProvider(
            Url("http://matrix.home"),
            MatrixClientAuthProviderDataStore.inMemory(
                MatrixClientAuthProviderData.classic(Url("http://matrix.home"), "access_token")
            ), {}
        ),
        syncBatchTokenStore = syncBatchTokenStore,
        coroutineContext = testScope.backgroundScope.coroutineContext,
    )

fun TrixnityBaseTest.getInMemoryAccountStore(setup: suspend AccountStore.() -> Unit = {}) = AccountStore(
    InMemoryAccountRepository(),
    RepositoryTransactionManagerMock(),
    ObservableCacheStatisticCollector(),
    testScope.backgroundScope,
    testScope.testClock,
).apply {
    scheduleSetup {
        init(backgroundScope)
        updateAccount {
            Account(
                olmPickleKey = null,
                baseUrl = "",
                userId = UserId("user", "server"),
                deviceId = "",
                accessToken = null,
                refreshToken = null,
                syncBatchToken = null,
                filter = null,
                profile = null,
            )
        }
        setup()
    }
}

fun TrixnityBaseTest.getInMemoryServerDataStore(setup: suspend ServerDataStore.() -> Unit = {}) = ServerDataStore(
    InMemoryServerDataRepository().apply {
        scheduleSetup {
            save(
                1,
                ServerData(GetVersions.Response(listOf("v1.11"), mapOf()), GetMediaConfig.Response(10_000), null)
            )
        }
    },
    RepositoryTransactionManagerMock(),
    ObservableCacheStatisticCollector(),
    testScope.backgroundScope,
    testScope.testClock,
).apply {
    scheduleSetup {
        init(backgroundScope)
        setup()
    }
}

fun TrixnityBaseTest.getInMemoryRoomAccountDataStore(setup: suspend RoomAccountDataStore.() -> Unit = {}) =
    RoomAccountDataStore(
        InMemoryRoomAccountDataRepository(),
        RepositoryTransactionManagerMock(),
        EventContentSerializerMappings.default,
        MatrixClientConfiguration(),
        ObservableCacheStatisticCollector(),
        testScope.backgroundScope,
        testScope.testClock,
    ).apply {
        scheduleSetup {
            init(backgroundScope)
            setup()
        }
    }

fun TrixnityBaseTest.getInMemoryGlobalAccountDataStore(setup: suspend GlobalAccountDataStore.() -> Unit = {}) =
    GlobalAccountDataStore(
        InMemoryGlobalAccountDataRepository(),
        RepositoryTransactionManagerMock(),
        EventContentSerializerMappings.default,
        MatrixClientConfiguration(),
        ObservableCacheStatisticCollector(),
        testScope.backgroundScope,
        testScope.testClock,
    ).apply {
        scheduleSetup {
            init(backgroundScope)
            setup()
        }
    }

fun TrixnityBaseTest.getInMemoryUserPresenceStore(setup: suspend UserPresenceStore.() -> Unit = {}) =
    UserPresenceStore(
        InMemoryUserPresenceRepository(),
        RepositoryTransactionManagerMock(),
        ObservableCacheStatisticCollector(),
        MatrixClientConfiguration(),
        testScope.backgroundScope,
        testScope.testClock,
    ).apply {
        scheduleSetup {
            init(backgroundScope)
            setup()
        }
    }

fun TrixnityBaseTest.getInMemoryOlmStore(setup: suspend OlmCryptoStore.() -> Unit = {}) = OlmCryptoStore(
    InMemoryOlmAccountRepository(),
    InMemoryOlmForgetFallbackKeyAfterRepository(),
    InMemoryOlmSessionRepository(),
    InMemoryInboundMegolmSessionRepository(),
    InMemoryInboundMegolmMessageIndexRepository(),
    InMemoryOutboundMegolmSessionRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    testScope.backgroundScope,
    testScope.testClock,
).apply {
    scheduleSetup {
        init(backgroundScope)
        setup()
    }
}

fun TrixnityBaseTest.getInMemoryKeyStore(setup: suspend KeyStore.() -> Unit = {}) = KeyStore(
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
    testScope.backgroundScope,
    testScope.testClock,
).apply {
    scheduleSetup {
        init(backgroundScope)
        setup()
    }
}

fun TrixnityBaseTest.getInMemoryRoomStore(setup: suspend RoomStore.() -> Unit = {}) = RoomStore(
    InMemoryRoomRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    testScope.backgroundScope,
    testScope.testClock,
).apply {
    scheduleSetup {
        init(backgroundScope)
        setup()
    }
}

fun TrixnityBaseTest.getInMemoryRoomTimelineStore(setup: suspend RoomTimelineStore.() -> Unit = {}) = RoomTimelineStore(
    InMemoryTimelineEventRepository(),
    InMemoryTimelineEventRelationRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    testScope.backgroundScope,
    testScope.testClock,
).apply {
    scheduleSetup {
        init(backgroundScope)
        setup()
    }
}

@MSC4354
fun TrixnityBaseTest.getInMemoryStickyEventStore(setup: suspend StickyEventStore.() -> Unit = {}) =
    StickyEventStore(
        InMemoryStickyEventRepository(),
        RepositoryTransactionManagerMock(),
        EventContentSerializerMappings.default,
        MatrixClientConfiguration(),
        ObservableCacheStatisticCollector(),
        testScope.backgroundScope,
        testScope.testClock,
    ).apply {
        scheduleSetup {
            init(backgroundScope)
            setup()
        }
    }

fun TrixnityBaseTest.getInMemoryRoomStateStore(setup: suspend RoomStateStore.() -> Unit = {}) = RoomStateStore(
    InMemoryRoomStateRepository(),
    RepositoryTransactionManagerMock(),
    EventContentSerializerMappings.default,
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    testScope.backgroundScope,
    testScope.testClock,
).apply {
    scheduleSetup {
        init(backgroundScope)
        setup()
    }
}

fun TrixnityBaseTest.getInMemoryRoomUserStore(setup: suspend RoomUserStore.() -> Unit = {}) = RoomUserStore(
    InMemoryRoomUserRepository(),
    InMemoryRoomUserReceiptsRepository(),
    RepositoryTransactionManagerMock(),
    MatrixClientConfiguration(),
    ObservableCacheStatisticCollector(),
    testScope.backgroundScope,
    testScope.testClock,
).apply {
    scheduleSetup {
        init(backgroundScope)
        setup()
    }
}

fun TrixnityBaseTest.getInMemoryMediaCacheMapping(setup: suspend MediaCacheMappingStore.() -> Unit = {}) =
    MediaCacheMappingStore(
        InMemoryMediaCacheMappingRepository(),
        RepositoryTransactionManagerMock(),
        MatrixClientConfiguration(),
        ObservableCacheStatisticCollector(),
        testScope.backgroundScope,
        testScope.testClock,
    ).apply {
        scheduleSetup {
            init(backgroundScope)
            setup()
        }
    }

fun TrixnityBaseTest.getInMemoryRoomOutboxMessageStore(setup: suspend RoomOutboxMessageStore.() -> Unit = {}) =
    RoomOutboxMessageStore(
        InMemoryRoomOutboxMessageRepository(),
        RepositoryTransactionManagerMock(),
        MatrixClientConfiguration(),
        ObservableCacheStatisticCollector(),
        testScope.backgroundScope,
        testScope.testClock,
    ).apply {
        scheduleSetup {
            init(backgroundScope)
            setup()
        }
    }

fun TrixnityBaseTest.getInMemoryNotificationStore(setup: suspend NotificationStore.() -> Unit = {}) =
    NotificationStore(
        InMemoryNotificationRepository(),
        InMemoryNotificationUpdateRepository(),
        InMemoryNotificationStateRepository(),
        NoOpRepositoryTransactionManager,
        MatrixClientConfiguration(),
        ObservableCacheStatisticCollector(),
        testScope.backgroundScope,
        testScope.testClock,
    ).apply {
        scheduleSetup {
            init(backgroundScope)
            setup()
        }
    }

fun TestScope.clearOutdatedKeys(keyStoreBuilder: () -> KeyStore) {
    val keyStore = keyStoreBuilder()
    backgroundScope.launch {
        keyStore.getOutdatedKeysFlow().filter { it.isNotEmpty() }.collect {
            keyStore.updateOutdatedKeys { setOf() }
        }
    }
}

fun TrixnityBaseTest.clearOutdatedKeys(keyStoreBuilder: () -> KeyStore) {
    val keyStore = keyStoreBuilder()
    testScope.backgroundScope.launch {
        keyStore.getOutdatedKeysFlow().filter { it.isNotEmpty() }.collect {
            keyStore.updateOutdatedKeys { setOf() }
        }
    }
}

@OptIn(KotestInternal::class)
suspend fun <T> continually(
    duration: Duration,
    test: suspend () -> T,
): T = withContext(NonDeterministicTestVirtualTimeEnabled) {
    val config = continuallyConfig<T> {
        this.duration = duration
        this.listener = { i, t -> }
    }
    io.kotest.assertions.nondeterministic.continually(config, test)
}

@OptIn(KotestInternal::class)
suspend fun <T> eventually(
    duration: Duration,
    test: suspend () -> T,
): T = withContext(NonDeterministicTestVirtualTimeEnabled) {
    val config = eventuallyConfig {
        this.duration = duration
        this.listener = { i, t -> }
    }
    io.kotest.assertions.nondeterministic.eventually(config, test)
}

@OptIn(KotestInternal::class)
suspend fun <T> retry(
    maxRetry: Int,
    timeout: Duration,
    delay: Duration = 1.seconds,
    multiplier: Int = 1,
    exceptionClass: KClass<out Throwable> = Exception::class,
    f: suspend () -> T,
): T = withContext(NonDeterministicTestVirtualTimeEnabled) {
    io.kotest.assertions.retry(
        maxRetry = maxRetry,
        timeout = timeout,
        delay = delay,
        multiplier = multiplier,
        exceptionClass = exceptionClass,
        f = f,
    )
}


class ClockMock : Clock {
    var nowValue: Instant = Instant.fromEpochMilliseconds(24242424)
    override fun now(): Instant = nowValue
}
