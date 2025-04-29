package net.folivo.trixnity.testutils

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.util.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIASerializer
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.core.ErrorResponseSerializer
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.MatrixServerException
import kotlin.jvm.JvmName

val matrixEndpointLog = KotlinLogging.logger("net.folivo.trixnity.testutils.MatrixJsonEndpoints")

data class CustomErrorResponse(val httpStatusCode: HttpStatusCode, override val message: String) : Exception(message)

@JvmName("matrixJsonEndpoint")
inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> MockEngineEndpointsConfig.matrixJsonEndpoint(
    endpoint: ENDPOINT,
    crossinline handler: suspend (REQUEST) -> RESPONSE
) {
    addHandler { request ->
        val expectedRequest = getExpectedRequest<ENDPOINT>(json, endpoint)
        if (expectedRequest.url.matches(request.url) && request.method == expectedRequest.method) {
            matrixEndpointLog.info { "handle request $request on expected endpoint $endpoint" }
            try {
                val requestString =
                    request.body.toByteArray().decodeToString().also { matrixEndpointLog.debug { "requestBody: $it" } }
                val requestSerializer = endpoint.requestSerializerBuilder(contentMappings, json, null)
                val requestBody: REQUEST =
                    when {
                        REQUEST::class == Unit::class -> Unit as REQUEST
                        requestSerializer != null -> json.decodeFromString(requestSerializer, requestString)
                        else -> json.decodeFromString(requestString)
                    }

                val responseBody = handler(requestBody)
                val responseSerializer = endpoint.responseSerializerBuilder(contentMappings, json, responseBody)
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

                    is CustomErrorResponse -> {
                        matrixEndpointLog.debug { "respond with CustomErrorResponse $exception" }
                        respond(
                            exception.message,
                            exception.httpStatusCode
                        )
                    }

                    else -> {
                        matrixEndpointLog.error(exception) { "unexpected error while handling request $request on expected endpoint $endpoint" }
                        throw UnsupportedOperationException("only MatrixServerExceptions are allowed")
                    }
                }
            }
        } else null
    }
}


@JvmName("matrixUIAJsonEndpoint")
inline fun <reified ENDPOINT : MatrixUIAEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> MockEngineEndpointsConfig.matrixJsonEndpoint(
    endpoint: ENDPOINT,
    crossinline handler: suspend (RequestWithUIA<REQUEST>) -> ResponseWithUIA<RESPONSE>
) {
    addHandler { request ->
        val expectedRequest = getExpectedRequest<ENDPOINT>(json, endpoint)
        if (expectedRequest.url.matches(request.url) && request.method == expectedRequest.method) {
            matrixEndpointLog.debug { "handle request $request on expected endpoint $endpoint" }
            try {
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
                                    headers = headersOf(
                                        HttpHeaders.ContentType,
                                        ContentType.Application.Json.toString()
                                    )
                                )
                            }
                        }
                    }

                    is ResponseWithUIA.Step -> respond(
                        json.encodeToString(responseBody.state),
                        HttpStatusCode.Unauthorized
                    )

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

                    is CustomErrorResponse -> {
                        matrixEndpointLog.debug { "respond with CustomErrorResponse $exception" }
                        respond(
                            exception.message,
                            exception.httpStatusCode
                        )
                    }

                    else -> {
                        matrixEndpointLog.error(exception) { "unexpected error while handling request $request on expected endpoint $endpoint" }
                        throw UnsupportedOperationException("only MatrixServerExceptions are allowed")
                    }
                }
            }
        } else null
    }
}

@PublishedApi
@OptIn(ExperimentalSerializationApi::class)
internal inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> getExpectedRequest(
    json: Json,
    endpoint: ENDPOINT
): HttpRequestData {
    val resources = Resources(Resources.Configuration().apply { serializersModule = json.serializersModule })
    return HttpRequestBuilder().apply {
        href(resources.resourcesFormat, endpoint, url)
        val endpointHttpMethod =
            serializer<ENDPOINT>().descriptor.annotations.filterIsInstance<HttpMethod>().firstOrNull()
                ?: throw IllegalArgumentException("matrix endpoint needs @Method annotation")
        method = HttpMethod(endpointHttpMethod.type.name)
        endpoint.requestContentType?.let { contentType(it) }
        endpoint.responseContentType?.let { accept(it) }
    }.build()
}

@PublishedApi
internal fun Url.matches(other: Url): Boolean {
    return rawSegments.size == other.rawSegments.size
            && rawSegments.mapIndexed { index, pathSegment ->
        when {
            pathSegment == "*" -> true
            pathSegment.endsWith("*") -> other.rawSegments.getOrNull(index)
                ?.startsWith(pathSegment.dropLast(1)) == true

            else -> pathSegment == other.rawSegments.getOrNull(index)
        }
    }.all { it }
            && parameters.toMap().all {
        if (it.value.size == 1 && it.value.first() == "*") true
        else it.value == other.parameters.getAll(it.key)
    }
}