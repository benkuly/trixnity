package net.folivo.trixnity.testutils

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.core.ErrorResponseSerializer
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException

val matrixEndpointLog = KotlinLogging.logger {}

inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> MockEngineConfig.matrixJsonEndpoint(
    json: Json,
    endpoint: ENDPOINT,
    requestSerializer: KSerializer<REQUEST>? = null,
    responseSerializer: KSerializer<RESPONSE>? = null,
    skipUrlCheck: Boolean = false,
    crossinline handler: suspend (REQUEST) -> RESPONSE
) {
    addHandler { request ->
        matrixEndpointLog.debug { "handle request $request on expected endpoint $endpoint" }
        try {
            val resources = Resources(Resources.Configuration().apply { serializersModule = json.serializersModule })
            val expectedRequest = HttpRequestBuilder().apply {
                href(resources.resourcesFormat, endpoint, url)
                method = endpoint.method
                contentType(endpoint.requestContentType)
                accept(endpoint.responseContentType)
            }.build()
            assertSoftly(request) {
                if (skipUrlCheck.not()) url shouldBe expectedRequest.url
                method shouldBe expectedRequest.method

            }
            request.method shouldBe expectedRequest.method

            val requestString =
                request.body.toByteArray().decodeToString().also { matrixEndpointLog.debug { "requestBody: $it" } }
            val requestBody: REQUEST =
                when {
                    REQUEST::class == Unit::class -> Unit as REQUEST
                    requestSerializer != null -> json.decodeFromString(requestSerializer, requestString)
                    else -> json.decodeFromString(requestString)
                }

            val responseBody = handler(requestBody)
            val responseString =
                when {
                    responseBody is Unit -> "{}"
                    responseSerializer != null -> json.encodeToString(responseSerializer, responseBody)
                    else -> json.encodeToString(responseBody)
                }.also { matrixEndpointLog.debug { "responseBody: $it" } }
            respond(
                responseString,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        } catch (exception: Throwable) {
            when (exception) {
                is MatrixServerException -> {
                    matrixEndpointLog.debug { "respond with MatrixServerException $exception" }
                    respond(
                        json.encodeToString(
                            ErrorResponseSerializer,
                            exception.errorResponse
                        ), exception.statusCode,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                else -> {
                    matrixEndpointLog.error(exception) { "unexpected error while handling request $request on expected endpoint $endpoint" }
                    throw UnsupportedOperationException("only MatrixServerExceptions are allowed")
                }
            }
        }
    }
}

