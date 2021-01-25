package net.folivo.trixnity.client.rest

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.http.URLProtocol.Companion.HTTP
import io.ktor.http.URLProtocol.Companion.HTTPS
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import net.folivo.trixnity.client.rest.api.ErrorResponse
import net.folivo.trixnity.client.rest.api.MatrixServerException
import net.folivo.trixnity.client.rest.api.room.RoomApiClient
import net.folivo.trixnity.client.rest.api.server.ServerApiClient
import net.folivo.trixnity.client.rest.api.sync.InMemorySyncBatchTokenService
import net.folivo.trixnity.client.rest.api.sync.SyncApiClient
import net.folivo.trixnity.client.rest.api.sync.SyncBatchTokenService
import net.folivo.trixnity.client.rest.api.user.UserApiClient
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.RoomEvent
import net.folivo.trixnity.core.model.events.StateEvent
import net.folivo.trixnity.core.serialization.event.DEFAULT_ROOM_EVENT_CONTENT_SERIALIZERS
import net.folivo.trixnity.core.serialization.event.DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMapping
import net.folivo.trixnity.core.serialization.event.createEventSerializersModule

class MatrixClient(
    internal val httpClient: HttpClient,
    syncBatchTokenService: SyncBatchTokenService = InMemorySyncBatchTokenService,
) {
    // TODO allow customization (needs https://youtrack.jetbrains.com/issue/KTOR-1628 to be fixed)
    private val roomEventSerializers: Set<EventContentSerializerMapping<out RoomEvent<*>, *>> =
        DEFAULT_ROOM_EVENT_CONTENT_SERIALIZERS
    private val stateEventSerializers: Set<EventContentSerializerMapping<out StateEvent<*>, *>> =
        DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS

    val server = ServerApiClient(httpClient)
    val user = UserApiClient(httpClient)
    val room = RoomApiClient(httpClient, roomEventSerializers, stateEventSerializers)
    val sync = SyncApiClient(httpClient, syncBatchTokenService)
}

@ExperimentalSerializationApi
private fun makeHttpClientConfig(
    properties: MatrixClientProperties,
    customModule: SerializersModule? = null
): HttpClientConfig<*>.() -> Unit { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1628 is fixed
    val eventSerializerModule = createEventSerializersModule()
//    val eventListSerializerModule = // I know it's hacky.
//        serializersModuleOf(ListSerializer(eventSerializerModule.getContextual(Event::class)!!))

    return {
        install(JsonFeature) {
            serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                // TODO although we don't want null values to be encoded, this flag is needed to encode the "type" of an event because of its default value type-field
                encodeDefaults = true
                serializersModule =
                    eventSerializerModule.let {
                        if (customModule != null) it + customModule else it
                    }
            })
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
        install(HttpTimeout) {

        }
    }
}

fun makeHttpClient(
    properties: MatrixClientProperties,
    customModule: SerializersModule? = null,
): HttpClient { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1628 is fixed
    return HttpClient {
        makeHttpClientConfig(properties, customModule)(this)
    }
}

fun <T : HttpClientEngineConfig> makeHttpClient(
    properties: MatrixClientProperties,
    httpClientEngineFactory: HttpClientEngineFactory<T>,
    customModule: SerializersModule? = null,
    httpClientEngineConfig: T.() -> Unit = {},
): HttpClient { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1628 is fixed
    return HttpClient(httpClientEngineFactory) {
        engine(httpClientEngineConfig)
        makeHttpClientConfig(properties, customModule)(this)
    }
}

fun MatrixId.e(): String { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1658 is fixed
    return full.encodeURLQueryComponent(true)
}