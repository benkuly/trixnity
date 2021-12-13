package net.folivo.trixnity.client.api

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.client.api.authentication.AuthenticationApiClient
import net.folivo.trixnity.client.api.devices.DevicesApiClient
import net.folivo.trixnity.client.api.keys.KeysApiClient
import net.folivo.trixnity.client.api.media.MediaApiClient
import net.folivo.trixnity.client.api.rooms.RoomsApiClient
import net.folivo.trixnity.client.api.server.ServerApiClient
import net.folivo.trixnity.client.api.sync.SyncApiClient
import net.folivo.trixnity.client.api.sync.SyncResponseSerializer
import net.folivo.trixnity.client.api.users.UsersApiClient
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import org.kodein.log.LoggerFactory

class MatrixApiClient(
    baseUrl: Url,
    baseHttpClient: HttpClient = HttpClient(),
    val eventContentSerializerMappings: EventContentSerializerMappings = createMatrixApiClientEventContentSerializerMappings(),
    loggerFactory: LoggerFactory = LoggerFactory.default,
    val json: Json = createMatrixApiClientJson(eventContentSerializerMappings, loggerFactory),
) {
    val accessToken = MutableStateFlow<String?>(null)

    val httpClient = MatrixHttpClient(baseHttpClient, json, baseUrl, accessToken)

    val authentication = AuthenticationApiClient(httpClient)
    val server = ServerApiClient(httpClient)
    val users = UsersApiClient(httpClient, json, eventContentSerializerMappings)
    val rooms = RoomsApiClient(httpClient, json, eventContentSerializerMappings)
    val sync = SyncApiClient(httpClient, loggerFactory)
    val keys = KeysApiClient(httpClient)
    val media = MediaApiClient(httpClient)
    val devices = DevicesApiClient(httpClient)
}

fun createMatrixApiClientEventContentSerializerMappings(customMappings: EventContentSerializerMappings? = null): EventContentSerializerMappings =
    DefaultEventContentSerializerMappings + customMappings

fun createMatrixApiClientJson(
    eventContentSerializerMappings: EventContentSerializerMappings,
    loggerFactory: LoggerFactory
): Json =
    createMatrixJson(
        eventContentSerializerMappings,
        SerializersModule { contextual(SyncResponseSerializer) },
        loggerFactory
    )