package net.folivo.trixnity.client.rest

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
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
import net.folivo.trixnity.client.rest.api.user.UserApiClient
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.serialization.EventSerializer
import net.folivo.trixnity.core.serialization.EventSerializer.EventSerializerDescriptor
import kotlin.reflect.KClass

class MatrixClient<T : HttpClientEngineConfig>(
    private val properties: MatrixClientProperties,
    private val customSerializers: Map<String, EventSerializerDescriptor<out Event<*>>> = mapOf(),
    private val customModule: SerializersModule? = null,
    httpClientEngineFactory: HttpClientEngineFactory<T>,
    httpClientEngineConfig: T.() -> Unit = {},
) {
    private val eventSerializer = EventSerializer(customSerializers)

    private val registeredEvents: Map<KClass<out Event<*>>, String> =
        (customSerializers + EventSerializer.defaultSerializers)
            .map { Pair(it.value.kclass, it.key) }.toMap()

    private val httpClientConfig: HttpClientConfig<T>.() -> Unit = {
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
            validateResponse { response ->
                val statusCode = response.status.value
                if (statusCode >= 300) {
                    val errorResponse = response.receive<ErrorResponse>()
                    throw MatrixServerException(response.status, errorResponse)
                }
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
            requestTimeoutMillis = 35000
        }
    }

    internal val httpClient = HttpClient(httpClientEngineFactory, httpClientConfig)


    val server = ServerApiClient(httpClient)
    val user = UserApiClient(httpClient)
    val room = RoomApiClient(httpClient, registeredEvents)

}