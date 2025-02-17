package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface MatrixClientServerApiClient : AutoCloseable {
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
}

interface MatrixClientServerApiClientFactory {
    fun create(
        baseUrl: Url? = null,
        authProvider: MatrixAuthProvider = MatrixAuthProvider.classicInMemory(),
        onLogout: suspend (LogoutInfo) -> Unit = { },
        eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
        json: Json = createMatrixEventJson(eventContentSerializerMappings),
        syncLoopDelay: Duration = 2.seconds,
        syncLoopErrorDelay: Duration = 5.seconds,
        httpClientEngine: HttpClientEngine? = null,
        httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
    ): MatrixClientServerApiClient {
        return MatrixClientServerApiClientImpl(
            baseUrl,
            authProvider,
            onLogout,
            eventContentSerializerMappings,
            json,
            syncLoopDelay,
            syncLoopErrorDelay,
            httpClientEngine,
            httpClientConfig,
        )
    }
}

class MatrixClientServerApiClientImpl(
    baseUrl: Url? = null,
    authProvider: MatrixAuthProvider = MatrixAuthProvider.classicInMemory(),
    onLogout: suspend (LogoutInfo) -> Unit = { },
    override val eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    override val json: Json = createMatrixEventJson(eventContentSerializerMappings),
    syncLoopDelay: Duration = 2.seconds,
    syncLoopErrorDelay: Duration = 5.seconds,
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
) : MatrixClientServerApiClient {
    private val httpClient = MatrixClientServerApiHttpClient(
        baseUrl = baseUrl,
        authProvider = authProvider,
        onLogout = onLogout,
        eventContentSerializerMappings = eventContentSerializerMappings,
        json = json,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig
    )

    override val appservice: AppserviceApiClient = AppserviceApiClientImpl(httpClient)
    override val authentication = AuthenticationApiClientImpl(httpClient)
    override val discovery = DiscoveryApiClientImpl(httpClient)
    override val server = ServerApiClientImpl(httpClient)
    override val user = UserApiClientImpl(httpClient, eventContentSerializerMappings)
    override val room = RoomApiClientImpl(httpClient, eventContentSerializerMappings)
    override val sync = SyncApiClientImpl(httpClient, syncLoopDelay, syncLoopErrorDelay)
    override val key = KeyApiClientImpl(httpClient, json)
    override val media = MediaApiClientImpl(httpClient)
    override val device = DeviceApiClientImpl(httpClient)
    override val push = PushApiClientImpl(httpClient)

    override fun close() {
        httpClient.close()
    }
}