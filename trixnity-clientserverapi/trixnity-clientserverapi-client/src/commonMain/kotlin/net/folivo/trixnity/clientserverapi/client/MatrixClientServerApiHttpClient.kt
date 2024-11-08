package net.folivo.trixnity.clientserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.clientserverapi.model.uia.*
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.ErrorResponseSerializer
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

private val log = KotlinLogging.logger { }

data class LogoutInfo(
    val isSoft: Boolean,
    val isLocked: Boolean,
)

class MatrixClientServerApiHttpClient(
    val baseUrl: Url? = null,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
    accessToken: MutableStateFlow<String?>,
    private val onLogout: suspend (LogoutInfo) -> Unit = { },
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
) : MatrixApiClient(
    eventContentSerializerMappings,
    json,
    httpClientEngine,
    {
        install(DefaultRequest) {
            accessToken.value?.let { bearerAuth(it) }
            if (baseUrl != null) url.takeFrom(baseUrl)
        }
        install(ConvertMediaPlugin)
        install(HttpRequestRetry) {
            retryIf { httpRequest, httpResponse ->
                (httpResponse.status == HttpStatusCode.TooManyRequests)
                    .also { if (it) log.warn { "rate limit exceeded for ${httpRequest.method} ${httpRequest.url}" } }
            }
            retryOnExceptionIf { _, throwable ->
                (throwable is MatrixServerException && throwable.statusCode == HttpStatusCode.TooManyRequests)
                    .also {
                        if (it) {
                            log.warn(if (log.isDebugEnabled()) throwable else null) { "rate limit exceeded" }
                        }
                    }
            }
            exponentialDelay(maxDelayMs = 30_000, respectRetryAfterHeader = true)
        }

        httpClientConfig?.invoke(this)
    }
) {
    override suspend fun onErrorResponse(response: HttpResponse, errorResponse: ErrorResponse) {
        if (response.status == HttpStatusCode.Unauthorized) {
            when (errorResponse) {
                is ErrorResponse.UnknownToken -> onLogout(LogoutInfo(errorResponse.softLogout, false))
                is ErrorResponse.UserLocked -> onLogout(LogoutInfo(errorResponse.softLogout, true))
                else -> {}
            }

        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified ENDPOINT : MatrixUIAEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> uiaRequest(
        endpoint: ENDPOINT,
        requestBody: REQUEST,
        noinline requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): Result<UIA<RESPONSE>> {
        val requestSerializer = endpoint.plainRequestSerializerBuilder(contentMappings, json) ?: serializer()
        val responseSerializer = endpoint.plainResponseSerializerBuilder(contentMappings, json) ?: serializer()
        return uiaRequest(requestBody, requestSerializer, responseSerializer) { jsonBody ->
            baseClient.request(endpoint) {
                val endpointHttpMethod =
                    serializer<ENDPOINT>().descriptor.annotations.filterIsInstance<HttpMethod>().firstOrNull()
                        ?: throw IllegalArgumentException("matrix endpoint needs @Method annotation")
                method = io.ktor.http.HttpMethod(endpointHttpMethod.type.name)
                endpoint.requestContentType?.let { contentType(it) }
                endpoint.responseContentType?.let { accept(it) }
                setBody(jsonBody)
                requestBuilder()
            }.body()
        }
    }

    suspend inline fun <reified ENDPOINT : MatrixUIAEndpoint<Unit, RESPONSE>, reified RESPONSE> uiaRequest(
        endpoint: ENDPOINT,
        noinline requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): Result<UIA<RESPONSE>> = uiaRequest(endpoint, Unit, requestBuilder)

    @PublishedApi
    internal suspend fun <REQUEST, RESPONSE> uiaRequest(
        requestBody: REQUEST,
        requestSerializer: KSerializer<REQUEST>,
        responseSerializer: KSerializer<RESPONSE>,
        jsonRequest: suspend (body: JsonObject) -> JsonObject
    ): Result<UIA<RESPONSE>> = kotlin.runCatching {
        val jsonBody = json.encodeToJsonElement(requestSerializer, requestBody)
        require(jsonBody is JsonObject)
        try {
            val plainResponse = jsonRequest(jsonBody)
            UIA.Success(json.decodeFromJsonElement(responseSerializer, plainResponse))
        } catch (responseException: ResponseException) {
            val response = responseException.response
            val responseText = response.bodyAsText()
            if (response.status == HttpStatusCode.Unauthorized) {
                val responseObject = json.decodeFromString<JsonObject>(responseText)
                val state = json.decodeFromJsonElement<UIAState>(responseObject)
                val errorCode = responseObject["errcode"]
                val getFallbackUrl: (AuthenticationType) -> Url = { authenticationType ->
                    URLBuilder().apply {
                        val localBaseUlr = baseUrl
                        if (localBaseUlr != null) takeFrom(localBaseUlr)
                        encodedPath += "_matrix/client/v3/auth/${authenticationType.name}/fallback/web"
                        state.session?.let { parameters.append("session", it) }
                    }.build()
                }
                val authenticate: suspend (AuthenticationRequest) -> Result<UIA<RESPONSE>> = { authenticationRequest ->
                    uiaRequest(
                        RequestWithUIA(
                            jsonBody,
                            AuthenticationRequestWithSession(authenticationRequest, state.session)
                        ),
                        serializer(),
                        responseSerializer,
                        jsonRequest
                    )
                }
                if (errorCode != null) {
                    val error = json.decodeFromJsonElement<ErrorResponse>(responseObject)
                    if (error is ErrorResponse.UnknownToken) {
                        onLogout(LogoutInfo(error.softLogout, false))
                    }
                    if (error is ErrorResponse.UserLocked) {
                        onLogout(LogoutInfo(error.softLogout, true))
                    }
                    UIA.Error(state, error, getFallbackUrl, authenticate)
                } else {
                    UIA.Step(state, getFallbackUrl, authenticate)
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