package net.folivo.trixnity.api.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.io.Source
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.serializer
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.default

private val log = KotlinLogging.logger("net.folivo.trixnity.api.client.MatrixApiClient")

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
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
        install(HttpCallValidator) {
            validateResponse { response ->
                val status = response.status
                if (status.isSuccess()) return@validateResponse
                log.trace { "try decode error response" }

                val errorResponse = json.decodeErrorResponse(response.bodyAsText())
                throw MatrixServerException(response.status, errorResponse, null)
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

