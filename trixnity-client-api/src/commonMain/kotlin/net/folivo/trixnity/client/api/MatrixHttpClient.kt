package net.folivo.trixnity.client.api

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import net.folivo.trixnity.client.api.model.ErrorResponse
import net.folivo.trixnity.client.api.model.ErrorResponseSerializer
import net.folivo.trixnity.client.api.model.uia.AuthenticationRequest
import net.folivo.trixnity.client.api.model.uia.AuthenticationRequestSerializer
import net.folivo.trixnity.client.api.model.uia.AuthenticationType
import net.folivo.trixnity.client.api.model.uia.UIAState

class MatrixHttpClient(
    initialHttpClient: HttpClient,
    val json: Json,
    private val baseUrl: Url,
    private val accessToken: MutableStateFlow<String?>,
    val onLogout: suspend (isSoft: Boolean) -> Unit = {}
) {
    val baseClient: HttpClient = initialHttpClient.config {
        install(JsonFeature) {
            serializer = KotlinxSerializer(json)
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
            baseClient.request(requestBuilder)
        } catch (responseException: ResponseException) {
            val response = responseException.response
            val responseText = response.readText()
            val errorResponse =
                try {
                    json.decodeFromString(ErrorResponseSerializer, responseText)
                } catch (error: Throwable) {
                    ErrorResponse.CustomErrorResponse("UNKNOWN", responseText)
                }
            if (response.status == HttpStatusCode.Unauthorized && errorResponse is ErrorResponse.UnknownToken) {
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
            val plainResponse = baseClient.request<String> {
                requestBuilder()
                require(this.body == EmptyContent) { "body must not be set via HttpRequestBuilder" }
                this.body = jsonBody
            }
            UIA.UIASuccess(json.decodeFromString(responseSerializer, plainResponse))
        } catch (responseException: ResponseException) {
            val response = responseException.response
            val responseText = response.readText()
            if (response.status == HttpStatusCode.Unauthorized) {
                val responseObject = json.decodeFromString<JsonObject>(responseText)
                val state = json.decodeFromJsonElement<UIAState>(responseObject)
                val errorCode = responseObject["errcode"]
                val getFallbackUrl: (AuthenticationType) -> Url = { authenticationType ->
                    URLBuilder().takeFrom(baseUrl).apply {
                        encodedPath += "/_matrix/client/v3/auth/${authenticationType.name}/fallback/web"
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
                    val error = json.decodeFromJsonElement<ErrorResponse>(responseObject)
                    if (error is ErrorResponse.UnknownToken) {
                        onLogout(error.softLogout)
                    }
                    UIA.UIAError(state, error, getFallbackUrl, authenticate)
                } else {
                    UIA.UIAStep(state, getFallbackUrl, authenticate)
                }
            } else {
                val errorResponse =
                    try {
                        json.decodeFromString(ErrorResponseSerializer, responseText)
                    } catch (error: Throwable) {
                        ErrorResponse.CustomErrorResponse("UNKNOWN", responseText)
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