package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

interface IMatrixClientServerApiClient {
    val accessToken: MutableStateFlow<String?>
    val httpClient: MatrixClientServerApiHttpClient

    val authentication: IAuthenticationApiClient
    val server: IServerApiClient
    val users: IUsersApiClient
    val rooms: IRoomsApiClient
    val sync: ISyncApiClient
    val keys: IKeysApiClient
    val media: IMediaApiClient
    val devices: IDevicesApiClient
    val push: IPushApiClient
}

class MatrixClientServerApiClient(
    baseUrl: Url? = null,
    onLogout: suspend (isSoft: Boolean) -> Unit = {},
    val eventContentSerializerMappings: EventContentSerializerMappings = createEventContentSerializerMappings(),
    val json: Json = createMatrixEventJson(eventContentSerializerMappings),
    httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) },
) : IMatrixClientServerApiClient {
    override val accessToken = MutableStateFlow<String?>(null)

    override val httpClient = MatrixClientServerApiHttpClient(
        baseUrl,
        json,
        eventContentSerializerMappings,
        accessToken,
        onLogout,
        httpClientFactory
    )

    override val authentication: IAuthenticationApiClient = AuthenticationApiClient(httpClient)
    override val server: IServerApiClient = ServerApiClient(httpClient)
    override val users: IUsersApiClient = UsersApiClient(httpClient, eventContentSerializerMappings)
    override val rooms: IRoomsApiClient = RoomsApiClient(httpClient, eventContentSerializerMappings)
    override val sync: ISyncApiClient = SyncApiClient(httpClient)
    override val keys: IKeysApiClient = KeysApiClient(httpClient, json)
    override val media: IMediaApiClient = MediaApiClient(httpClient)
    override val devices: IDevicesApiClient = DevicesApiClient(httpClient)
    override val push: IPushApiClient = PushApiClient(httpClient)
}