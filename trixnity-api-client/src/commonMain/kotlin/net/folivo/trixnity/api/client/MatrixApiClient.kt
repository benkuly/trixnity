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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.ErrorResponseSerializer
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException

open class MatrixApiClient(
    val baseUrl: Url? = null,
    val json: Json,
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
        responseSerializer: KSerializer<RESPONSE>? = null,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): Result<RESPONSE> =
        request(endpoint, Unit, responseSerializer = responseSerializer, requestBuilder = requestBuilder)

    suspend inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> request(
        endpoint: ENDPOINT,
        body: REQUEST,
        requestSerializer: KSerializer<REQUEST>? = null,
        responseSerializer: KSerializer<RESPONSE>? = null,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): Result<RESPONSE> = kotlin.runCatching {
        try {
            unsafeRequest(endpoint, body, requestSerializer, responseSerializer, requestBuilder)
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

    suspend inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> unsafeRequest(
        endpoint: ENDPOINT,
        requestBody: REQUEST,
        requestSerializer: KSerializer<REQUEST>? = null,
        responseSerializer: KSerializer<RESPONSE>? = null,
        requestBuilder: HttpRequestBuilder.() -> Unit = {}
    ): RESPONSE {
        val response = baseClient.request(endpoint) {
            method = endpoint.method
            contentType(endpoint.requestContentType)
            accept(endpoint.responseContentType)
            if (requestBody != Unit) {
                if (requestSerializer != null) setBody(json.encodeToJsonElement(requestSerializer, requestBody))
                else setBody(requestBody)
            }
            requestBuilder()
        }
        return if (responseSerializer != null) json.decodeFromJsonElement(responseSerializer, response.body())
        else response.body()
    }
}