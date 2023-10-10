package net.folivo.trixnity.api.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

open class MatrixApiClient(
    val contentMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    val json: Json = createMatrixEventJson(contentMappings),
    httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = defaultTrixnityHttpClient(),
) {
    val baseClient: HttpClient = httpClientFactory {
        install(ContentNegotiation) {
            json(json)
        }
        install(Resources)
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
        expectSuccess = true
        followRedirects = false
    }

    open suspend fun onErrorResponse(response: HttpResponse, errorResponse: ErrorResponse) {

    }

    suspend inline fun <reified ENDPOINT : MatrixEndpoint<Unit, RESPONSE>, reified RESPONSE> request(
        endpoint: ENDPOINT,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): Result<RESPONSE> = request(endpoint, Unit, requestBuilder)

    suspend inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> request(
        endpoint: ENDPOINT,
        body: REQUEST,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): Result<RESPONSE> = runCatching<RESPONSE> {
        try {
            unsafeRequest(endpoint, body, requestBuilder)
        } catch (responseException: ResponseException) {
            when (responseException) {
                is RedirectResponseException -> throw responseException
                else -> {
                    val response = responseException.response
                    val responseText = response.bodyAsText()
                    val errorResponse =
                        try {
                            json.decodeFromString(ErrorResponseSerializer, responseText)
                        } catch (error: Throwable) {
                            ErrorResponse.CustomErrorResponse("UNKNOWN", responseText)
                        }
                    onErrorResponse(response, errorResponse)
                    throw MatrixServerException(response.status, errorResponse)
                }
            }
        }
    }

    @PublishedApi
    @OptIn(ExperimentalSerializationApi::class)
    internal suspend inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> unsafeRequest(
        endpoint: ENDPOINT,
        requestBody: REQUEST,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): RESPONSE {
        val requestSerializer = endpoint.requestSerializerBuilder(contentMappings, json, requestBody)
        val response = baseClient.request(endpoint) {
            val endpointHttpMethod =
                serializer<ENDPOINT>().descriptor.annotations.filterIsInstance<HttpMethod>().firstOrNull()
                    ?: throw IllegalArgumentException("matrix endpoint needs @Method annotation")
            method = io.ktor.http.HttpMethod(endpointHttpMethod.type.name)
            endpoint.requestContentType?.let { contentType(it) }
            endpoint.responseContentType?.let { accept(it) }
            if (requestBody != Unit) {
                if (requestSerializer != null) setBody(json.encodeToJsonElement(requestSerializer, requestBody))
                else setBody(requestBody)
            } else {
                if (endpoint.requestContentType == ContentType.Application.Json
                    && (method == io.ktor.http.HttpMethod.Post || method == io.ktor.http.HttpMethod.Put)
                ) setBody("{}")
            }
            requestBuilder()
        }
        val responseSerializer = endpoint.responseSerializerBuilder(contentMappings, json, null)
        val forceJson =
            serializer<ENDPOINT>().descriptor.annotations.filterIsInstance<ForceJson>().isNotEmpty()
        return when {
            forceJson -> json.decodeFromString(responseSerializer ?: serializer(), response.bodyAsText())
            responseSerializer != null -> json.decodeFromJsonElement(responseSerializer, response.body())
            else -> response.body()
        }
    }
}

fun defaultTrixnityHttpClient(
    userAgent: String = "Trixnity",
    config: HttpClientConfig<*>.() -> Unit = {},
): ((HttpClientConfig<*>.() -> Unit) -> HttpClient) = { baseConfig ->
    HttpClient {
        baseConfig()
        install(UserAgent) {
            agent = userAgent
        }
        config()
    }
}