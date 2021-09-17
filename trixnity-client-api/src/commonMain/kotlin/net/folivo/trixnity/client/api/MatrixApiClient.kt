package net.folivo.trixnity.client.api

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.client.api.authentication.AuthenticationApiClient
import net.folivo.trixnity.client.api.keys.KeysApiClient
import net.folivo.trixnity.client.api.rooms.RoomsApiClient
import net.folivo.trixnity.client.api.server.ServerApiClient
import net.folivo.trixnity.client.api.sync.SyncApiClient
import net.folivo.trixnity.client.api.sync.SyncResponseSerializer
import net.folivo.trixnity.client.api.users.UsersApiClient
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import org.kodein.log.LoggerFactory

class MatrixApiClient(
    private val hostname: String,
    private val hostport: Int = 443,
    private val secure: Boolean = true,
    val accessToken: MutableStateFlow<String?> = MutableStateFlow(null),
    baseHttpClient: HttpClient = HttpClient(),
    customMappings: EventContentSerializerMappings? = null,
    loggerFactory: LoggerFactory = LoggerFactory.default
) {

    val eventContentSerializerMappings = DefaultEventContentSerializerMappings + customMappings
    val json =
        createMatrixJson(
            loggerFactory,
            eventContentSerializerMappings,
            SerializersModule { contextual(SyncResponseSerializer) })

    val httpClient: HttpClient = baseHttpClient.config {
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
            host = hostname
            port = hostport
            url.protocol = if (secure) HTTPS else HTTP
            url.encodedPath = "/_matrix/client/" + url.encodedPath
            accessToken.value?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            header(HttpHeaders.ContentType, Application.Json)
            accept(Application.Json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
    }

    val authentication = AuthenticationApiClient(httpClient)
    val server = ServerApiClient(httpClient)
    val users = UsersApiClient(httpClient, eventContentSerializerMappings)
    val rooms = RoomsApiClient(httpClient, json, eventContentSerializerMappings)
    val sync = SyncApiClient(httpClient, loggerFactory)
    val keys = KeysApiClient(httpClient)
}

fun MatrixId.e(): String { // TODO remove when https://youtrack.jetbrains.com/issue/KTOR-1658 is fixed
    return full.encodeURLQueryComponent(true)
}