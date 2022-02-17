package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.clientserverapi.model.sync.SyncResponseSerializer
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

class MatrixClientServerApiClient(
    baseUrl: Url,
    baseHttpClient: HttpClient = HttpClient(),
    onLogout: suspend (isSoft: Boolean) -> Unit = {},
    val eventContentSerializerMappings: EventContentSerializerMappings = createMatrixClientServerApiClientEventContentSerializerMappings(),
    val json: Json = createMatrixClientServerApiClientJson(eventContentSerializerMappings),
) {
    val accessToken = MutableStateFlow<String?>(null)

    val httpClient = MatrixHttpClient(baseHttpClient, json, baseUrl, accessToken, onLogout)

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

fun createMatrixClientServerApiClientJson(
    eventContentSerializerMappings: EventContentSerializerMappings,
): Json =
    createMatrixJson(
        eventContentSerializerMappings,
        SerializersModule { contextual(SyncResponseSerializer) },
    )