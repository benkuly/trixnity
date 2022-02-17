package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequestSerializer
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationType
import net.folivo.trixnity.clientserverapi.model.uia.UIAState

class MatrixHttpClient(
    initialHttpClient: HttpClient,
    val json: Json,
    private val baseUrl: Url,
    private val accessToken: MutableStateFlow<String?>,
    val onLogout: suspend (isSoft: Boolean) -> Unit = {}
) {
    val baseClient: HttpClient = initialHttpClient.config {
        install(ContentNegotiation) {
            json(json)
        }
        install(DefaultRequest) {
            val requestUrl = url.encodedPath
            url.takeFrom(URLBuilder().takeFrom(baseUrl).apply {
                encodedPath += requestUrl
            })
            accessToken.value?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            if (!requestUrl.startsWith("_matrix/media")) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                accept(ContentType.Application.Json)
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
    }

    suspend inline fun <reified T> request(
        requestBuilder: HttpRequestBuilder.() -> Unit
    ): Result<T> = kotlin.runCatching {
        try {
            baseClient.request(requestBuilder).body()
        } catch (responseException: ResponseException) {
            val response = responseException.response
            val responseText = response.bodyAsText()
            val errorResponse =
                try {
                    json.decodeFromString(
                        net.folivo.trixnity.clientserverapi.model.ErrorResponseSerializer,
                        responseText
                    )
                } catch (error: Throwable) {
                    net.folivo.trixnity.clientserverapi.model.ErrorResponse.CustomErrorResponse("UNKNOWN", responseText)
                }
            if (response.status == HttpStatusCode.Unauthorized && errorResponse is net.folivo.trixnity.clientserverapi.model.ErrorResponse.UnknownToken) {
                onLogout(errorResponse.softLogout)
            }
            throw MatrixServerException(response.status, errorResponse)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun <B, R> uiaRequest(
        body: B,
        bodySerializer: KSerializer<B>,
        responseSerializer: KSerializer<R>,
        requestBuilder: HttpRequestBuilder.() -> Unit
    ): Result<UIA<R>> = kotlin.runCatching {
        val jsonBody = json.encodeToJsonElement(bodySerializer, body)
        require(jsonBody is JsonObject)
        try {
            val plainResponse = baseClient.request {
                requestBuilder()
                require(this.body == EmptyContent) { "body must not be set via HttpRequestBuilder" }
                setBody(jsonBody)
            }.body<String>()
            UIA.UIASuccess(json.decodeFromString(responseSerializer, plainResponse))
        } catch (responseException: ResponseException) {
            val response = responseException.response
            val responseText = response.bodyAsText()
            if (response.status == HttpStatusCode.Unauthorized) {
                val responseObject = json.decodeFromString<JsonObject>(responseText)
                val state = json.decodeFromJsonElement<UIAState>(responseObject)
                val errorCode = responseObject["errcode"]
                val getFallbackUrl: (AuthenticationType) -> Url = { authenticationType ->
                    URLBuilder().takeFrom(baseUrl).apply {
                        encodedPath += "_matrix/client/v3/auth/${authenticationType.name}/fallback/web"
                        state.session?.let { parameters.append("session", it) }
                    }.build()
                }
                val authenticate: suspend (AuthenticationRequest) -> Result<UIA<R>> = { authenticationRequest ->
                    val authBody = JsonObject(
                        buildMap {
                            putAll(jsonBody)
                            put(
                                "auth",
                                json.encodeToJsonElement(
                                    AuthenticationRequestSerializer(state.session),
                                    authenticationRequest
                                )
                            )
                        }
                    )
                    uiaRequest(authBody, serializer(), responseSerializer, requestBuilder)
                }
                if (errorCode != null) {
                    val error = json.decodeFromJsonElement<net.folivo.trixnity.clientserverapi.model.ErrorResponse>(
                        responseObject
                    )
                    if (error is net.folivo.trixnity.clientserverapi.model.ErrorResponse.UnknownToken) {
                        onLogout(error.softLogout)
                    }
                    UIA.UIAError(state, error, getFallbackUrl, authenticate)
                } else {
                    UIA.UIAStep(state, getFallbackUrl, authenticate)
                }
            } else {
                val errorResponse =
                    try {
                        json.decodeFromString(
                            net.folivo.trixnity.clientserverapi.model.ErrorResponseSerializer,
                            responseText
                        )
                    } catch (error: Throwable) {
                        net.folivo.trixnity.clientserverapi.model.ErrorResponse.CustomErrorResponse(
                            "UNKNOWN",
                            responseText
                        )
                    }
                throw MatrixServerException(response.status, errorResponse)
            }
        }
    }

    suspend inline fun <reified B, reified R> uiaRequest(
        body: B,
        noinline requestBuilder: HttpRequestBuilder.() -> Unit
    ): Result<UIA<R>> {
        return uiaRequest(
            body = body,
            bodySerializer = serializer(),
            responseSerializer = serializer(),
            requestBuilder = requestBuilder
        )
    }

    suspend inline fun <reified R> uiaRequest(
        noinline requestBuilder: HttpRequestBuilder.() -> Unit
    ): Result<UIA<R>> {
        return uiaRequest(
            body = JsonObject(mapOf()),
            bodySerializer = serializer(),
            responseSerializer = serializer(),
            requestBuilder = requestBuilder
        )
    }
}