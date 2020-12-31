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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.serializersModuleOf
import net.folivo.trixnity.client.rest.api.ErrorResponse
import net.folivo.trixnity.client.rest.api.MatrixServerException
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.serialization.EventSerializer

class MatrixClient(
    properties: MatrixClientProperties,
    customSerializers: Map<String, KSerializer<out Event<*>>> = mapOf()
) {
    private val client = HttpClient {
        install(JsonFeature) {
            serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                serializersModule = serializersModuleOf(EventSerializer(customSerializers))
            })
        }
        defaultRequest {
            host = properties.homeServer.hostname
            port = properties.homeServer.port
            url.encodedPath = "/_matrix/client" + url.encodedPath
            header(HttpHeaders.Authorization, "Bearer ${properties.token}")
            header(HttpHeaders.ContentType, Application.Json)
            header(HttpHeaders.Accept, Application.Json)
            expectSuccess = false
        }
        HttpResponseValidator {
            validateResponse { response: HttpResponse ->
                val statusCode = response.status.value
                if (statusCode >= 300) {
                    val errorResponse = response.receive<ErrorResponse>()
                    throw MatrixServerException(response.status, errorResponse)
                }
            }
        }
    }
}