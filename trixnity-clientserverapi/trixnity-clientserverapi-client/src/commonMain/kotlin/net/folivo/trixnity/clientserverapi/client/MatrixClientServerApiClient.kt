package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.client.defaultTrixnityHttpClientFactory
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface MatrixClientServerApiClient {
    val accessToken: MutableStateFlow<String?>
    val httpClient: MatrixClientServerApiHttpClient

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

class MatrixClientServerApiClientImpl(
    baseUrl: Url? = null,
    onLogout: suspend (LogoutInfo) -> Unit = { },
    override val eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    override val json: Json = createMatrixEventJson(eventContentSerializerMappings),
    httpClientFactory: (config: HttpClientConfig<*>.() -> Unit) -> HttpClient = defaultTrixnityHttpClientFactory(),
    syncLoopDelay: Duration = 2.seconds,
    syncLoopErrorDelay: Duration = 5.seconds
) : MatrixClientServerApiClient {
    override val accessToken = MutableStateFlow<String?>(null)

    override val httpClient = MatrixClientServerApiHttpClient(
        baseUrl,
        eventContentSerializerMappings,
        json,
        accessToken,
        onLogout,
        httpClientFactory
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
}