package net.folivo.trixnity.client.api

import io.ktor.client.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.client.api.authentication.AuthenticationApiClient
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
    hostname: String,
    hostport: Int = 443,
    secure: Boolean = true,
    baseHttpClient: HttpClient = HttpClient(),
    customMappings: EventContentSerializerMappings? = null,
    loggerFactory: LoggerFactory = LoggerFactory.default
) {
    val accessToken = MutableStateFlow<String?>(null)
    val eventContentSerializerMappings = DefaultEventContentSerializerMappings + customMappings
    val json =
        createMatrixJson(
            loggerFactory,
            eventContentSerializerMappings,
            SerializersModule { contextual(SyncResponseSerializer) })

    val httpClient = MatrixHttpClient(baseHttpClient, json, hostname, hostport, secure, accessToken)

    val authentication = AuthenticationApiClient(httpClient)
    val server = ServerApiClient(httpClient)
    val users = UsersApiClient(httpClient, json, eventContentSerializerMappings)
    val rooms = RoomsApiClient(httpClient, json, eventContentSerializerMappings)
    val sync = SyncApiClient(httpClient, loggerFactory)
    val keys = KeysApiClient(httpClient)
    val media = MediaApiClient(httpClient)
}