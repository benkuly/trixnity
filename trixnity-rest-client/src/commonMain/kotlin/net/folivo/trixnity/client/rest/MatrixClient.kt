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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.serializersModuleOf
import net.folivo.trixnity.client.rest.api.ErrorResponse
import net.folivo.trixnity.client.rest.api.MatrixServerException
import net.folivo.trixnity.client.rest.api.room.RoomApiClient
import net.folivo.trixnity.client.rest.api.server.ServerApiClient
import net.folivo.trixnity.client.rest.api.sync.InMemorySyncBatchTokenService
import net.folivo.trixnity.client.rest.api.sync.SyncApiClient
import net.folivo.trixnity.client.rest.api.sync.SyncBatchTokenService
import net.folivo.trixnity.client.rest.api.user.UserApiClient
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.serialization.EventSerializer
import net.folivo.trixnity.core.serialization.EventSerializer.EventSerializerDescriptor
import kotlin.reflect.KClass

class MatrixClient(
    internal val httpClient: HttpClient,
    syncBatchTokenService: SyncBatchTokenService = InMemorySyncBatchTokenService,
    customSerializers: Map<String, EventSerializerDescriptor<out Event<*>>> = mapOf(),
) {
    private val registeredEvents: Map<KClass<out Event<*>>, String> =
        (customSerializers + EventSerializer.defaultSerializers)
            .map { Pair(it.value.kclass, it.key) }.toMap()

    val server = ServerApiClient(httpClient)
    val user = UserApiClient(httpClient)
    val room = RoomApiClient(httpClient, registeredEvents)
    val sync = SyncApiClient(httpClient, syncBatchTokenService)
}

fun <T : HttpClientEngineConfig> makeClient(
    properties: MatrixClientProperties,
    httpClientEngineFactory: HttpClientEngineFactory<T>,
    customSerializers: Map<String, EventSerializerDescriptor<out Event<*>>> = mapOf(),
    customModule: SerializersModule? = null,
    httpClientEngineConfig: T.() -> Unit = {},
): HttpClient { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1628 is fixed
    val eventSerializer = EventSerializer(customSerializers)
    return HttpClient(httpClientEngineFactory) {
        engine(httpClientEngineConfig)
        install(JsonFeature) {
            serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                // TODO although we don't want null values to be encoded, this flag is needed to encode the "type" of an event because of its default value type-field
                encodeDefaults = true
                serializersModule =
                    (serializersModuleOf(eventSerializer)
                            + serializersModuleOf(ListSerializer(eventSerializer))
                            ).let {
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
                        throw MatrixServerException(response.status, ErrorResponse("UNKNOWN", response.readText()))
                    }
                throw MatrixServerException(response.status, errorResponse)
            }
        }
        install(DefaultRequest) {
            host = properties.homeServer.hostname
            port = properties.homeServer.port
            url.encodedPath = "/_matrix/client/" + url.encodedPath
            header(HttpHeaders.Authorization, "Bearer ${properties.token}")
            header(HttpHeaders.ContentType, Application.Json)
            accept(Application.Json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 35000 //FIXME only for the one sync request
        }
    }
}

fun MatrixId.e(): String { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1658 is fixed
    return full.encodeURLQueryComponent(true)
}