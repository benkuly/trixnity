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

private val log = KotlinLogging.logger("net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient")

interface MatrixClientServerApiClient : AutoCloseable {
    val baseClient: MatrixClientServerApiBaseClient
    val appservice: AppserviceApiClient
    val authentication: AuthenticationApiClient
    val discovery: DiscoveryApiClient
    val server: ServerApiClient
    val user: UserApiClient
    val room: RoomApiClient
    val sync: SyncApiClient
    val key: KeyApiClient
    val media: MediaApiClient
    val device: DeviceApiClient
    val push: PushApiClient

    val eventContentSerializerMappings: EventContentSerializerMappings
    val json: Json

    suspend fun closeSuspending()
}

interface MatrixClientServerApiClientFactory {
    fun create(
        baseUrl: Url? = null,
        authProvider: MatrixClientAuthProvider<*>? = null,
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

    companion object Default : MatrixClientServerApiClientFactory
}

class MatrixClientServerApiClientImpl(
    baseUrl: Url? = null,
    private val authProvider: MatrixClientAuthProvider<*>? = null,
    override val eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    override val json: Json = createMatrixEventJson(eventContentSerializerMappings),
    syncBatchTokenStore: SyncBatchTokenStore = SyncBatchTokenStore.inMemory(),
    syncErrorDelayConfig: RetryFlowDelayConfig = RetryFlowDelayConfig.sync,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
) : MatrixClientServerApiClient {
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
    override val authentication = AuthenticationApiClientImpl(baseClient, authProvider)
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