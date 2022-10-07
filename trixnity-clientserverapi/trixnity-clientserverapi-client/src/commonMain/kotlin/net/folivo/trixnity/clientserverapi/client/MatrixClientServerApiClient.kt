package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

interface MatrixClientServerApiClient {
    val accessToken: MutableStateFlow<String?>
    val httpClient: MatrixClientServerApiHttpClient

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
    httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) },
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

    override val authentication = AuthenticationApiClientImpl(httpClient)
    override val discovery = DiscoveryApiClientImpl(httpClient)
    override val server = ServerApiClientImpl(httpClient)
    override val users = UsersApiClientImpl(httpClient, eventContentSerializerMappings)
    override val rooms = RoomsApiClientImpl(httpClient, eventContentSerializerMappings)
    override val sync = SyncApiClientImpl(httpClient)
    override val keys = KeysApiClientImpl(httpClient, json)
    override val media = MediaApiClientImpl(httpClient)
    override val devices = DevicesApiClientImpl(httpClient)
    override val push = PushApiClientImpl(httpClient)
}