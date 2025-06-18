package net.folivo.trixnity.clientserverapi.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.clientserverapi.model.media.Media
import net.folivo.trixnity.clientserverapi.model.uia.*
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

private val log = KotlinLogging.logger("net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiBaseClient")

data class LogoutInfo(
    val isSoft: Boolean,
    val isLocked: Boolean,
)

interface MatrixAuthProvider : AuthProvider {
    companion object
}

/**
 * Plugin to fully disable auth for requests with AuthRequired set to NEVER
 */
private val SkipAuthenticationIfDisabled = createClientPlugin("SkipAuthenticationIfDisabled") {
    onRequest { request, _ ->
        if (request.attributes.getOrNull(AuthRequired.attributeKey) == AuthRequired.NEVER) {
            request.attributes.put(AuthCircuitBreaker, Unit)
        }
    }
}

@PublishedApi
internal val IsUIA = AttributeKey<Unit>("IsUIA")

private class UIAInterception(val body: JsonObject, val errorResponse: ErrorResponse?) : RuntimeException()

class MatrixClientServerApiBaseClient(
    val baseUrl: Url? = null,
    authProvider: MatrixAuthProvider = MatrixAuthProvider.classicInMemory(),
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
) : MatrixApiClient(
    eventContentSerializerMappings,
    json,
    httpClientEngine,
    {
        install(DefaultRequest) {
            if (baseUrl != null) url.takeFrom(baseUrl)
        }

        install(SkipAuthenticationIfDisabled)
        install(Auth) {
            providers.add(authProvider)
        }
        install(createClientPlugin("UIAInterception") {
            on(Send) { request ->
                val call = proceed(request)
                val response = call.response
                if (response.status != HttpStatusCode.Unauthorized) return@on call
                if (request.attributes.getOrNull(IsUIA) == null) return@on call
                val body = json.decodeFromString<JsonObject>(response.bodyAsText())
                val errorResponse = if (body["errcode"] != null) json.decodeErrorResponse(body) else null
                if (errorResponse is ErrorResponse.UnknownToken || errorResponse is ErrorResponse.UserLocked) return@on call
                throw UIAInterception(body, errorResponse)
            }
        })
        install(ConvertMediaPlugin)
        install(ContentNegotiation) {
            ignoreType(Media::class)
        }
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
    suspend inline fun <reified ENDPOINT : MatrixUIAEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> uiaRequest(
        endpoint: ENDPOINT,
        requestBody: REQUEST,
        noinline requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): Result<UIA<RESPONSE>> {
        val requestSerializer = endpoint.plainRequestSerializerBuilder(contentMappings, json) ?: serializer()
        val responseSerializer = endpoint.plainResponseSerializerBuilder(contentMappings, json) ?: serializer()
        return uiaRequest(requestBody, requestSerializer, responseSerializer) { jsonBody ->
            val responseBody = baseClient.request(endpoint) {
                val annotations = serializer<ENDPOINT>().descriptor.annotations
                val endpointHttpMethod = annotations.filterIsInstance<HttpMethod>().firstOrNull()
                    ?: throw IllegalArgumentException("matrix endpoint needs @Method annotation")
                val authRequired = annotations.filterIsInstance<Auth>().firstOrNull()?.required ?: AuthRequired.YES
                attributes.put(AuthRequired.attributeKey, authRequired)
                attributes.put(IsUIA, Unit)
                method = io.ktor.http.HttpMethod(endpointHttpMethod.type.name)
                endpoint.requestContentType?.let { contentType(it) }
                endpoint.responseContentType?.let { accept(it) }
                setBody(json.encodeToString(jsonBody))
                requestBuilder()
            }.bodyAsText()
            json.decodeFromString(responseBody)
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
        } catch (exception: UIAInterception) {
            val responseObject = exception.body
            val state = json.decodeFromJsonElement<UIAState>(responseObject)
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

            val errorResponse = exception.errorResponse
            if (errorResponse != null) {
                UIA.Error(state, errorResponse, getFallbackUrl, authenticate)
            } else {
                UIA.Step(state, getFallbackUrl, authenticate)
            }
        }
    }
}