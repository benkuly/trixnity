package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

class MatrixClientServerApiClient(
    baseUrl: Url? = null,
    val json: Json = createMatrixJson(),
    onLogout: suspend (isSoft: Boolean) -> Unit = {},
    val eventContentSerializerMappings: EventContentSerializerMappings = createMatrixClientServerApiClientEventContentSerializerMappings(),
    httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) },
) {
    val accessToken = MutableStateFlow<String?>(null)

    val httpClient = MatrixClientServerApiHttpClient(baseUrl, json, accessToken, onLogout, httpClientFactory)

    val authentication = AuthenticationApiClient(httpClient)
    val server = ServerApiClient(httpClient)
    val users = UsersApiClient(httpClient, json, eventContentSerializerMappings)
    val rooms = RoomsApiClient(httpClient, json, eventContentSerializerMappings)
    val sync = SyncApiClient(httpClient)
    val keys = KeysApiClient(httpClient, json)
    val media = MediaApiClient(httpClient)
    val devices = DevicesApiClient(httpClient)
    val push = PushApiClient(httpClient)
}

fun createMatrixClientServerApiClientEventContentSerializerMappings(customMappings: EventContentSerializerMappings? = null): EventContentSerializerMappings =
    DefaultEventContentSerializerMappings + customMappings