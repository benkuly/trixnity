package de.connect2x.trixnity.api.client

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.decodeErrorResponse
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.prepareRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.charsets.MalformedInputException
import kotlinx.io.Source
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.serializer

private val log = Logger("de.connect2x.trixnity.api.client.MatrixApiClient")

open class MatrixApiClient(
    val contentMappings: EventContentSerializerMappings = EventContentSerializerMappings.default,
    val json: Json = createMatrixEventJson(contentMappings),
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
) : AutoCloseable {
    private val finalHttpClientConfig: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(json)
        }
        install(Resources)
        install(HttpTimeout)
        install(HttpCallValidator) {
            validateResponse { response ->
                val status = response.status
                if (status.isSuccess()) return@validateResponse
                log.trace { "try decode error response" }

                val exceptionResponseText = try {
                    response.bodyAsText()
                } catch (_: MalformedInputException) {
                    "<body failed decoding>"
                }
                val errorResponse = json.decodeErrorResponse(exceptionResponseText)

                if (errorResponse != null) {
                    throw MatrixServerException(status, errorResponse, null)
                }

                val statusCode = status.value
                val exception = when (statusCode) {
                    in 300..399 -> RedirectResponseException(response, exceptionResponseText)
                    in 400..499 -> ClientRequestException(response, exceptionResponseText)
                    in 500..599 -> ServerResponseException(response, exceptionResponseText)
                    else -> ResponseException(response, exceptionResponseText)
                }
                throw exception
            }
        }
        expectSuccess = false

        install(PlatformUserAgentPlugin)

        httpClientConfig?.invoke(this)
    }
    val baseClient: HttpClient =
        if (httpClientEngine == null) HttpClient(finalHttpClientConfig)
        else HttpClient(httpClientEngine, finalHttpClientConfig)

    suspend inline fun <reified ENDPOINT : MatrixEndpoint<Unit, RESPONSE>, reified RESPONSE> request(
        endpoint: ENDPOINT,
        requestBuilder: HttpRequestBuilder.() -> Unit = {},
    ): Result<RESPONSE> = withRequest(endpoint, requestBuilder) { it }

    suspend inline fun <reified ENDPOINT : MatrixEndpoint<Unit, RESPONSE>, reified RESPONSE, T> withRequest(
        endpoint: ENDPOINT,
        requestBuilder: HttpRequestBuilder.() -> Unit = {},
        noinline responseHandler: suspend (RESPONSE) -> T
    ): Result<T> = withRequest(endpoint, Unit, requestBuilder, responseHandler)

    suspend inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> request(
        endpoint: ENDPOINT,
        body: REQUEST,
        requestBuilder: HttpRequestBuilder.() -> Unit = {},
    ): Result<RESPONSE> = withRequest(endpoint, body, requestBuilder) { it }

    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE, T> withRequest(
        endpoint: ENDPOINT,
        body: REQUEST,
        requestBuilder: HttpRequestBuilder.() -> Unit = {},
        noinline responseHandler: suspend (RESPONSE) -> T
    ): Result<T> = runCatching<T> {
        val requestSerializer = endpoint.requestSerializerBuilder(contentMappings, json, body)
        val request = baseClient.prepareRequest(endpoint) {
            val annotations = serializer<ENDPOINT>().descriptor.annotations
            val endpointHttpMethod = annotations.filterIsInstance<HttpMethod>().firstOrNull()
                ?: throw IllegalArgumentException("matrix endpoint needs @Method annotation")
            val authRequired = annotations.filterIsInstance<Auth>().firstOrNull()?.required ?: AuthRequired.YES
            attributes.put(AuthRequired.attributeKey, authRequired)
            method = io.ktor.http.HttpMethod(endpointHttpMethod.type.name)
            endpoint.responseContentType?.let { accept(it) }
            if (body != Unit) {
                endpoint.requestContentType?.let { contentType(it) }
                when {
                    requestSerializer != null -> setBody(json.encodeToString(requestSerializer, body))
                    endpoint.requestContentType == ContentType.Application.Json ->
                        setBody(json.encodeToString(serializer(), body))

                    else -> setBody(body)
                }
            } else {
                if (endpoint.requestContentType == ContentType.Application.Json
                    && (method == io.ktor.http.HttpMethod.Post || method == io.ktor.http.HttpMethod.Put)
                ) {
                    endpoint.requestContentType?.let { contentType(it) }
                    setBody("{}")
                }
            }
            requestBuilder()
        }
        val responseSerializer = endpoint.responseSerializerBuilder(contentMappings, json, null)
        request.execute { response ->
            responseHandler(
                when {
                    responseSerializer != null ->
                        json.decodeFromSource(responseSerializer, response.body<Source>())

                    endpoint.responseContentType == ContentType.Application.Json ->
                        json.decodeFromSource(serializer(), response.body<Source>())

                    else -> response.body()
                }
            )
        }
    }

    override fun close() {
        baseClient.close()
    }
}

