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
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

open class MatrixApiClient(
    val baseUrl: Url? = null,
    val json: Json = createMatrixJson(),
    val contentMappings: EventContentSerializerMappings = createEventContentSerializerMappings(),
    httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) },
) {
    val baseClient: HttpClient = httpClientFactory {
        install(ContentNegotiation) {
            json(json)
        }
        install(Resources)
        install(DefaultRequest) {
            if (baseUrl != null) url.takeFrom(baseUrl)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
        expectSuccess = true
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

    @PublishedApi
    @OptIn(ExperimentalSerializationApi::class)
    internal suspend inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> unsafeRequest(
        endpoint: ENDPOINT,
        requestBody: REQUEST,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): RESPONSE {
        val requestSerializer = endpoint.requestSerializerBuilder(contentMappings, json)
        val response = baseClient.request(endpoint) {
            val endpointHttpMethod =
                serializer<ENDPOINT>().descriptor.annotations.filterIsInstance<HttpMethod>().firstOrNull()
                    ?: throw IllegalArgumentException("matrix endpoint needs @Method annotation")
            method = io.ktor.http.HttpMethod(endpointHttpMethod.type.name)
            contentType(endpoint.requestContentType)
            accept(endpoint.responseContentType)
            if (requestBody != Unit) {
                if (requestSerializer != null) setBody(json.encodeToJsonElement(requestSerializer, requestBody))
                else setBody(requestBody)
            }
            requestBuilder()
        }
        val responseSerializer = endpoint.responseSerializerBuilder(contentMappings, json)
        return if (responseSerializer != null) json.decodeFromJsonElement(responseSerializer, response.body())
        else response.body()
    }
}