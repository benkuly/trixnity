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
    override val baseClient = MatrixClientServerApiBaseClient(
        baseUrl = baseUrl,
        authProvider = authProvider,
        onLogout = onLogout,
        eventContentSerializerMappings = eventContentSerializerMappings,
        json = json,
        httpClientEngine = httpClientEngine,
        httpClientConfig = httpClientConfig
    )

    override val appservice: AppserviceApiClient = AppserviceApiClientImpl(baseClient)
    override val authentication = AuthenticationApiClientImpl(baseClient)
    override val discovery = DiscoveryApiClientImpl(baseClient)
    override val server = ServerApiClientImpl(baseClient)
    override val user = UserApiClientImpl(baseClient, eventContentSerializerMappings)
    override val room = RoomApiClientImpl(baseClient, eventContentSerializerMappings)
    override val sync = SyncApiClientImpl(baseClient, syncLoopDelay, syncLoopErrorDelay)
    override val key = KeyApiClientImpl(baseClient, json)
    override val media = MediaApiClientImpl(baseClient)
    override val device = DeviceApiClientImpl(baseClient)
    override val push = PushApiClientImpl(baseClient)

    override fun close() {
        baseClient.close()
    }
}