package net.folivo.trixnity.client.api

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.client.api.model.sync.SyncResponseSerializer
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings

class MatrixApiClient(
    baseUrl: Url,
    baseHttpClient: HttpClient = HttpClient(),
    val eventContentSerializerMappings: EventContentSerializerMappings = createMatrixApiClientEventContentSerializerMappings(),
    val json: Json = createMatrixApiClientJson(eventContentSerializerMappings),
) {
    val accessToken = MutableStateFlow<String?>(null)

    val httpClient = MatrixHttpClient(baseHttpClient, json, baseUrl, accessToken)

    val authentication = AuthenticationApiClient(httpClient)
    val server = ServerApiClient(httpClient)
    val users = UsersApiClient(httpClient, json, eventContentSerializerMappings)
    val rooms = RoomsApiClient(httpClient, json, eventContentSerializerMappings)
    val sync = SyncApiClient(httpClient)
    val keys = KeysApiClient(httpClient)
    val media = MediaApiClient(httpClient)
    val devices = DevicesApiClient(httpClient)
}

fun createMatrixApiClientEventContentSerializerMappings(customMappings: EventContentSerializerMappings? = null): EventContentSerializerMappings =
    DefaultEventContentSerializerMappings + customMappings

fun createMatrixApiClientJson(
    eventContentSerializerMappings: EventContentSerializerMappings,
): Json =
    createMatrixJson(
        eventContentSerializerMappings,
        SerializersModule { contextual(SyncResponseSerializer) },
    )