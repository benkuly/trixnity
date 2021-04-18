package net.folivo.trixnity.client.rest

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.http.URLProtocol.Companion.HTTP
import io.ktor.http.URLProtocol.Companion.HTTPS
import net.folivo.trixnity.client.rest.api.ErrorResponse
import net.folivo.trixnity.client.rest.api.MatrixServerException
import net.folivo.trixnity.client.rest.api.room.RoomApiClient
import net.folivo.trixnity.client.rest.api.server.ServerApiClient
import net.folivo.trixnity.client.rest.api.sync.InMemorySyncBatchTokenService
import net.folivo.trixnity.client.rest.api.sync.SyncApiClient
import net.folivo.trixnity.client.rest.api.sync.SyncBatchTokenService
import net.folivo.trixnity.client.rest.api.user.UserApiClient
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.createJson
import net.folivo.trixnity.core.serialization.event.DEFAULT_ROOM_EVENT_CONTENT_SERIALIZERS
import net.folivo.trixnity.core.serialization.event.DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMapping

class MatrixClient(
    httpClient: HttpClient,
    properties: MatrixClientProperties,
    syncBatchTokenService: SyncBatchTokenService = InMemorySyncBatchTokenService,
    customRoomEventContentSerializers: Set<EventContentSerializerMapping<out RoomEventContent>> = emptySet(),
    customStateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>> = emptySet(),
) {
    private val roomEventContentSerializers: Set<EventContentSerializerMapping<out RoomEventContent>> =
        DEFAULT_ROOM_EVENT_CONTENT_SERIALIZERS + customRoomEventContentSerializers
    private val stateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>> =
        DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS + customStateEventContentSerializers

    val json = createJson(roomEventContentSerializers, stateEventContentSerializers)

    val configuredHttpClient: HttpClient = httpClient.config {
        install(JsonFeature) {
            serializer = KotlinxSerializer(json)
        }
        install(HttpCallValidator) {
            handleResponseException { responseException ->
                if (responseException !is ResponseException) return@handleResponseException
                val response = responseException.response
                val errorResponse =
                    try {
                        response.receive<ErrorResponse>()
                    } catch (error: Throwable) {
                        throw MatrixServerException(
                            response.status,
                            ErrorResponse("UNKNOWN", response.readText())
                        )
                    }
                throw MatrixServerException(response.status, errorResponse)
            }
        }
        install(DefaultRequest) {
            host = properties.homeServer.hostname
            port = properties.homeServer.port
            url.protocol = if (properties.homeServer.secure) HTTPS else HTTP
            url.encodedPath = "/_matrix/client/" + url.encodedPath
            header(HttpHeaders.Authorization, "Bearer ${properties.token}")
            header(HttpHeaders.ContentType, Application.Json)
            accept(Application.Json)
        }
        install(HttpTimeout)
    }

    val server = ServerApiClient(configuredHttpClient)
    val user = UserApiClient(configuredHttpClient)
    val room = RoomApiClient(
        configuredHttpClient,
        json,
        roomEventContentSerializers,
        stateEventContentSerializers
    )
    val sync = SyncApiClient(configuredHttpClient, syncBatchTokenService)
}

fun MatrixId.e(): String { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1658 is fixed
    return full.encodeURLQueryComponent(true)
}