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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.capturedKClass
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.serializersModuleOf
import net.folivo.trixnity.client.rest.api.ErrorResponse
import net.folivo.trixnity.client.rest.api.MatrixServerException
import net.folivo.trixnity.client.rest.api.server.ServerApiClient
import net.folivo.trixnity.client.rest.api.user.UserApiClient
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.serialization.EventSerializer
import kotlin.reflect.KClass

class MatrixClient<T : HttpClientEngineConfig>(
    private val properties: MatrixClientProperties,
    private val customSerializers: Map<String, KSerializer<out Event<*>>> = mapOf(),
    private val customModule: SerializersModule? = null,
    httpClientEngineFactory: HttpClientEngineFactory<T>,
    httpClientEngineConfig: T.() -> Unit = {},
) {

    @ExperimentalSerializationApi
    val registeredEvents: Map<KClass<out Event<*>>, String> =
        (customSerializers + EventSerializer.defaultSerializers)
            .mapNotNull { entry ->
                val kclass = entry.value.descriptor.capturedKClass as KClass<out Event<*>>?
                kclass?.let { Pair(kclass, entry.key) }
            }.toMap()

    private val httpClientConfig: HttpClientConfig<T>.() -> Unit = {
        engine(httpClientEngineConfig)
        install(JsonFeature) {
            serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                serializersModule = serializersModuleOf(EventSerializer(customSerializers)).let {
                    if (customModule != null) it + customModule else it
                }
            })
        }
        expectSuccess = false
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

}