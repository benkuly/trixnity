package net.folivo.trixnity.clientserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.utils.RetryFlowDelayConfig
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger("net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient")

interface MatrixClientServerApiClient : AutoCloseable {
    val baseClient: MatrixClientServerApiBaseClient
    val appservice: AppserviceApiClient
    val authentication: AuthenticationApiClient
    val discovery: DiscoveryApiClient
    val server: ServerApiClient
    val user: UserApiClient

    @Deprecated("changed to user", ReplaceWith("user"))
    val users: UserApiClient
        get() = user
    val room: RoomApiClient

    @Deprecated("changed to room", ReplaceWith("room"))
    val rooms: RoomApiClient
        get() = room
    val sync: SyncApiClient
    val key: KeyApiClient

    @Deprecated("changed to key", ReplaceWith("key"))
    val keys: KeyApiClient
        get() = key
    val media: MediaApiClient
    val device: DeviceApiClient

    @Deprecated("changed to device", ReplaceWith("device"))
    val devices: DeviceApiClient
        get() = device
    val push: PushApiClient

    val eventContentSerializerMappings: EventContentSerializerMappings
    val json: Json

    suspend fun closeSuspending()
}

interface MatrixClientServerApiClientFactory {
    @Deprecated("use variant with syncDelayConfig")
    fun create(
        baseUrl: Url? = null,
        authProvider: MatrixAuthProvider = MatrixAuthProvider.classicInMemory(),
        eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
        json: Json = createMatrixEventJson(eventContentSerializerMappings),
        syncBatchTokenStore: SyncBatchTokenStore = SyncBatchTokenStore.inMemory(),
        syncLoopDelay: Duration = 2.seconds,
        syncLoopErrorDelay: Duration = 5.seconds,
        coroutineContext: CoroutineContext = Dispatchers.Default,
        httpClientEngine: HttpClientEngine? = null,
        httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
    ): MatrixClientServerApiClient =
        MatrixClientServerApiClientImpl(
            baseUrl = baseUrl,
            authProvider = authProvider,
            eventContentSerializerMappings = eventContentSerializerMappings,
            json = json,
            syncBatchTokenStore = syncBatchTokenStore,
            syncErrorDelayConfig = RetryFlowDelayConfig(
                scheduleBase = syncLoopErrorDelay,
                scheduleFactor = 1.0,
                scheduleJitter = 1.0..1.0
            ),
            coroutineContext = coroutineContext,
            httpClientEngine = httpClientEngine,
            httpClientConfig = httpClientConfig,
        )

    fun create(
        baseUrl: Url? = null,
        authProvider: MatrixAuthProvider = MatrixAuthProvider.classicInMemory(),
        eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
        json: Json = createMatrixEventJson(eventContentSerializerMappings),
        syncBatchTokenStore: SyncBatchTokenStore = SyncBatchTokenStore.inMemory(),
        syncErrorDelayConfig: RetryFlowDelayConfig = RetryFlowDelayConfig.sync,
        coroutineContext: CoroutineContext = Dispatchers.Default,
        httpClientEngine: HttpClientEngine? = null,
        httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
    ): MatrixClientServerApiClient =
        MatrixClientServerApiClientImpl(
            baseUrl = baseUrl,
            authProvider = authProvider,
            eventContentSerializerMappings = eventContentSerializerMappings,
            json = json,
            syncBatchTokenStore = syncBatchTokenStore,
            syncErrorDelayConfig = syncErrorDelayConfig,
            coroutineContext = coroutineContext,
            httpClientEngine = httpClientEngine,
            httpClientConfig = httpClientConfig,
        )
}

class MatrixClientServerApiClientImpl(
    baseUrl: Url? = null,
    authProvider: MatrixAuthProvider = MatrixAuthProvider.classicInMemory(),
    override val eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    override val json: Json = createMatrixEventJson(eventContentSerializerMappings),
    syncBatchTokenStore: SyncBatchTokenStore = SyncBatchTokenStore.inMemory(),
    syncErrorDelayConfig: RetryFlowDelayConfig = RetryFlowDelayConfig.sync,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
) : MatrixClientServerApiClient {

    @Deprecated("use variant with syncDelayConfig")
    constructor(
        baseUrl: Url? = null,
        authProvider: MatrixAuthProvider = MatrixAuthProvider.classicInMemory(),
        eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
        json: Json = createMatrixEventJson(eventContentSerializerMappings),
        syncBatchTokenStore: SyncBatchTokenStore = SyncBatchTokenStore.inMemory(),
        syncLoopDelay: Duration = 2.seconds,
        syncLoopErrorDelay: Duration = 5.seconds,
        coroutineContext: CoroutineContext = Dispatchers.Default,
        httpClientEngine: HttpClientEngine? = null,
        httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
    ) : this(
        baseUrl = baseUrl,
        authProvider = authProvider,
        eventContentSerializerMappings = eventContentSerializerMappings,
        json = json,
        syncBatchTokenStore = syncBatchTokenStore,
        syncErrorDelayConfig = RetryFlowDelayConfig(
            scheduleBase = syncLoopErrorDelay,
            scheduleFactor = 1.0,
            scheduleJitter = 1.0..1.0
        ),
        coroutineContext = coroutineContext,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig,
    )

    override val baseClient = MatrixClientServerApiBaseClient(
        baseUrl = baseUrl,
        authProvider = authProvider,
        eventContentSerializerMappings = eventContentSerializerMappings,
        json = json,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig
    )

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
        log.error(exception) { "There was an unexpected exception in sync. This should never happen!!!" }
    }
    private val coroutineScope: CoroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]) + coroutineExceptionHandler)

    override val appservice: AppserviceApiClient = AppserviceApiClientImpl(baseClient)
    override val authentication = AuthenticationApiClientImpl(baseClient)
    override val discovery = DiscoveryApiClientImpl(baseClient)
    override val server = ServerApiClientImpl(baseClient)
    override val user = UserApiClientImpl(baseClient, eventContentSerializerMappings)
    override val room = RoomApiClientImpl(baseClient, eventContentSerializerMappings)
    override val sync = SyncApiClientImpl(
        baseClient = baseClient,
        coroutineScope = coroutineScope,
        syncBatchTokenStore = syncBatchTokenStore,
        syncErrorDelayConfig = syncErrorDelayConfig,
    )
    override val key = KeyApiClientImpl(baseClient, json)
    override val media = MediaApiClientImpl(baseClient)
    override val device = DeviceApiClientImpl(baseClient)
    override val push = PushApiClientImpl(baseClient)

    override fun close() {
        coroutineScope.cancel()
        baseClient.close()
    }

    override suspend fun closeSuspending() {
        val job = coroutineScope.coroutineContext.job
        close()
        job.join()
    }
}