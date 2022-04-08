package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

class MatrixClientServerApiClient(
    baseUrl: Url? = null,
    val json: Json = createMatrixJson(),
    onLogout: suspend (isSoft: Boolean) -> Unit = {},
    val eventContentSerializerMappings: EventContentSerializerMappings = createEventContentSerializerMappings(),
    httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) },
) {
    val accessToken = MutableStateFlow<String?>(null)

    val httpClient = MatrixClientServerApiHttpClient(
        baseUrl,
        json,
        eventContentSerializerMappings,
        accessToken,
        onLogout,
        httpClientFactory
    )

    val authentication: IAuthenticationApiClient = AuthenticationApiClient(httpClient)
    val server: IServerApiClient = ServerApiClient(httpClient)
    val users: IUsersApiClient = UsersApiClient(httpClient, json, eventContentSerializerMappings)
    val rooms: IRoomsApiClient = RoomsApiClient(httpClient, eventContentSerializerMappings)
    val sync: ISyncApiClient = SyncApiClient(httpClient)
    val keys: IKeysApiClient = KeysApiClient(httpClient, json)
    val media: IMediaApiClient = MediaApiClient(httpClient)
    val devices: IDevicesApiClient = DevicesApiClient(httpClient)
    val push: IPushApiClient = PushApiClient(httpClient)
}