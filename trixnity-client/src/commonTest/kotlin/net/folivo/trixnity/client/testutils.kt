package net.folivo.trixnity.client

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
import net.folivo.trixnity.client.mocks.RepositoryTransactionManagerMock
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.cache.ObservableCacheStatisticCollector
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.clientserverapi.client.*
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
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.scheduleSetup
import net.folivo.trixnity.test.utils.testClock
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.scopedMockEngineWithEndpoints
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
    contentMappings: EventContentSerializerMappings = createDefaultEventContentSerializerMappings(),
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
                filterId = null,
                backgroundFilterId = null,
                displayName = null,
                avatarUrl = null,
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
                ServerData(GetVersions.Response(listOf("v1.11"), mapOf()), GetMediaConfig.Response(10_000))
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
        DefaultEventContentSerializerMappings,
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
        DefaultEventContentSerializerMappings,
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

fun TrixnityBaseTest.getInMemoryRoomStateStore(setup: suspend RoomStateStore.() -> Unit = {}) = RoomStateStore(
    InMemoryRoomStateRepository(),
    RepositoryTransactionManagerMock(),
    DefaultEventContentSerializerMappings,
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