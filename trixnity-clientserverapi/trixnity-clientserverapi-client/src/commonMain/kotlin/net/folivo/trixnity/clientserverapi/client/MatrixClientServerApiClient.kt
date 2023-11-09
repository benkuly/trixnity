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
    val users: UsersApiClient
    val rooms: RoomsApiClient
    val sync: SyncApiClient
    val keys: KeysApiClient
    val media: MediaApiClient
    val devices: DevicesApiClient
    val push: PushApiClient

    val eventContentSerializerMappings: EventContentSerializerMappings
    val json: Json
}

class MatrixClientServerApiClientImpl(
    baseUrl: Url? = null,
    onLogout: suspend (isSoft: Boolean) -> Unit = {},
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
    override val users = UsersApiClientImpl(httpClient, eventContentSerializerMappings)
    override val rooms = RoomsApiClientImpl(httpClient, eventContentSerializerMappings)
    override val sync = SyncApiClientImpl(httpClient, syncLoopDelay, syncLoopErrorDelay)
    override val keys = KeysApiClientImpl(httpClient, json)
    override val media = MediaApiClientImpl(httpClient)
    override val devices = DevicesApiClientImpl(httpClient)
    override val push = PushApiClientImpl(httpClient)
}