package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequestSerializer
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationType
import net.folivo.trixnity.clientserverapi.model.uia.UIAState
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.ErrorResponseSerializer
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException

class MatrixClientServerApiHttpClient(
    baseUrl: Url? = null,
    json: Json,
    accessToken: MutableStateFlow<String?>,
    private val onLogout: suspend (isSoft: Boolean) -> Unit = {},
    httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) },
) : MatrixApiClient(
    baseUrl,
    json,
    {
        httpClientFactory {
            it()
            install(DefaultRequest) {
                accessToken.value?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
        }
    }
) {
    override suspend fun onErrorResponse(response: HttpResponse, errorResponse: ErrorResponse) {
        if (response.status == HttpStatusCode.Unauthorized && errorResponse is ErrorResponse.UnknownToken) {
            onLogout(errorResponse.softLogout)
        }
    }

    suspend inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> uiaRequest(
        endpoint: ENDPOINT,
        requestBody: REQUEST,
        noinline requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): Result<UIA<RESPONSE>> = uiaRequest(requestBody, serializer(), serializer()) { jsonBody ->
        baseClient.request(endpoint) {
            method = endpoint.method
            contentType(endpoint.requestContentType)
            accept(endpoint.responseContentType)
            if (jsonBody.isNotEmpty()) setBody(jsonBody)
            requestBuilder()
        }.body()
    }

    suspend inline fun <reified ENDPOINT : MatrixEndpoint<Unit, RESPONSE>, reified RESPONSE> uiaRequest(
        endpoint: ENDPOINT,
        noinline requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): Result<UIA<RESPONSE>> = uiaRequest(endpoint, Unit, requestBuilder)

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun <REQUEST, RESPONSE> uiaRequest(
        requestBody: REQUEST,
        requestSerializer: KSerializer<REQUEST>,
        responseSerializer: KSerializer<RESPONSE>,
        request: suspend (body: JsonObject) -> JsonObject
    ): Result<UIA<RESPONSE>> = kotlin.runCatching {
        val jsonBody = json.encodeToJsonElement(requestSerializer, requestBody)
        require(jsonBody is JsonObject)
        try {
            val plainResponse = request(jsonBody)
            UIA.UIASuccess(json.decodeFromJsonElement(responseSerializer, plainResponse))
        } catch (responseException: ResponseException) {
            val response = responseException.response
            val responseText = response.bodyAsText()
            if (response.status == HttpStatusCode.Unauthorized) {
                val responseObject = json.decodeFromString<JsonObject>(responseText)
                val state = json.decodeFromJsonElement<UIAState>(responseObject)
                val errorCode = responseObject["errcode"]
                val getFallbackUrl: (AuthenticationType) -> Url = { authenticationType ->
                    URLBuilder().apply {
                        if (baseUrl != null) takeFrom(baseUrl)
                        encodedPath += "_matrix/client/v3/auth/${authenticationType.name}/fallback/web"
                        state.session?.let { parameters.append("session", it) }
                    }.build()
                }
                val authenticate: suspend (AuthenticationRequest) -> Result<UIA<RESPONSE>> = { authenticationRequest ->
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
                    uiaRequest(authBody, serializer(), responseSerializer, request)
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
                        json.decodeFromString(
                            ErrorResponseSerializer,
                            responseText
                        )
                    } catch (error: Throwable) {
                        ErrorResponse.CustomErrorResponse(
                            "UNKNOWN",
                            responseText
                        )
                    }
                throw MatrixServerException(response.status, errorResponse)
            }
        }
    }
}