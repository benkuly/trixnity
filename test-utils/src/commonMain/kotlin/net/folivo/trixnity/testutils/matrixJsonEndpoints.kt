package net.folivo.trixnity.testutils

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIASerializer
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.core.ErrorResponseSerializer
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.jvm.JvmName

val matrixEndpointLog = KotlinLogging.logger {}

@OptIn(ExperimentalSerializationApi::class)
@JvmName("matrixJsonEndpoint")
inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> MockEngineConfig.matrixJsonEndpoint(
    json: Json,
    contentMappings: EventContentSerializerMappings,
    endpoint: ENDPOINT,
    skipUrlCheck: Boolean = false,
    crossinline handler: suspend (REQUEST) -> RESPONSE
) {
    addHandler { request ->
        matrixEndpointLog.debug { "handle request $request on expected endpoint $endpoint" }
        try {
            val resources = Resources(Resources.Configuration().apply { serializersModule = json.serializersModule })
            val expectedRequest = HttpRequestBuilder().apply {
                href(resources.resourcesFormat, endpoint, url)
                val endpointHttpMethod =
                    serializer<ENDPOINT>().descriptor.annotations.filterIsInstance<HttpMethod>().firstOrNull()
                        ?: throw IllegalArgumentException("matrix endpoint needs @Method annotation")
                method = io.ktor.http.HttpMethod(endpointHttpMethod.type.name)
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
            val requestSerializer = endpoint.requestSerializerBuilder(contentMappings, json)
            val requestBody: REQUEST =
                when {
                    REQUEST::class == Unit::class -> Unit as REQUEST
                    requestSerializer != null -> json.decodeFromString(requestSerializer, requestString)
                    else -> json.decodeFromString(requestString)
                }

            val responseBody = handler(requestBody)
            val responseSerializer = endpoint.responseSerializerBuilder(contentMappings, json)
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

@OptIn(ExperimentalSerializationApi::class)
@JvmName("matrixUIAJsonEndpoint")
inline fun <reified ENDPOINT : MatrixUIAEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> MockEngineConfig.matrixJsonEndpoint(
    json: Json,
    contentMappings: EventContentSerializerMappings,
    endpoint: ENDPOINT,
    skipUrlCheck: Boolean = false,
    crossinline handler: suspend (RequestWithUIA<REQUEST>) -> ResponseWithUIA<RESPONSE>
) {
    addHandler { request ->
        matrixEndpointLog.debug { "handle request $request on expected endpoint $endpoint" }
        try {
            val resources = Resources(Resources.Configuration().apply { serializersModule = json.serializersModule })
            val expectedRequest = HttpRequestBuilder().apply {
                href(resources.resourcesFormat, endpoint, url)
                val endpointHttpMethod =
                    serializer<ENDPOINT>().descriptor.annotations.filterIsInstance<HttpMethod>().firstOrNull()
                        ?: throw IllegalArgumentException("matrix endpoint needs @Method annotation")
                method = HttpMethod(endpointHttpMethod.type.name)
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
            val requestSerializer: KSerializer<REQUEST> =
                endpoint.plainRequestSerializerBuilder(contentMappings, json) ?: serializer()
            val requestBody: RequestWithUIA<REQUEST> =
                json.decodeFromString(
                    RequestWithUIASerializer(requestSerializer),
                    requestString
                )
            when (val responseBody = handler(requestBody)) {
                is ResponseWithUIA.Success -> {
                    when (val responseValue = responseBody.value) {
                        is Unit, null -> respond(
                            "{}".also { matrixEndpointLog.debug { "responseBody: $it" } },
                            HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        )
                        else -> {
                            val responseSerializer: KSerializer<RESPONSE> =
                                endpoint.plainResponseSerializerBuilder(contentMappings, json) ?: serializer()
                            respond(
                                json.encodeToString(responseSerializer, responseValue)
                                    .also { matrixEndpointLog.debug { "responseBody: $it" } },
                                HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            )
                        }
                    }
                }
                is ResponseWithUIA.Step -> respond(json.encodeToString(responseBody.state), HttpStatusCode.Unauthorized)
                is ResponseWithUIA.Error ->
                    respond(
                        json.encodeToString(JsonObject(buildMap {
                            putAll(json.encodeToJsonElement(responseBody.state).jsonObject)
                            putAll(json.encodeToJsonElement(responseBody.errorResponse).jsonObject)
                        })).also { matrixEndpointLog.debug { "responseBody: $it" } },
                        HttpStatusCode.Unauthorized,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
            }
        } catch (exception: Throwable) {
            when (exception) {
                is MatrixServerException -> {
                    matrixEndpointLog.debug { "respond with MatrixServerException $exception" }
                    respond(
                        json.encodeToString(
                            ErrorResponseSerializer,
                            exception.errorResponse
                        ).also { matrixEndpointLog.debug { "responseBody: $it" } }, exception.statusCode,
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