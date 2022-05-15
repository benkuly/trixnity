package net.folivo.trixnity.api.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.jvm.JvmName

class MatrixEndpointContext<ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, REQUEST, RESPONSE>(
    val endpoint: ENDPOINT,
    val requestBody: REQUEST,
    val call: ApplicationCall,
)

val withoutAuthAttributeKey = AttributeKey<Boolean>("matrixEndpointConfig")

// TODO inject json when ktor 2.0.0 is released
inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> Route.matrixEndpoint(
    json: Json,
    mappings: EventContentSerializerMappings,
    crossinline handler: suspend (MatrixEndpointContext<ENDPOINT, REQUEST, RESPONSE>) -> RESPONSE,
) {
    matrixEndpointResource<ENDPOINT, REQUEST, RESPONSE> { endpoint ->
        val requestSerializer: KSerializer<REQUEST>? = endpoint.requestSerializerBuilder(mappings, json)
        val requestBody: REQUEST =
            when {
                REQUEST::class == Unit::class -> Unit as REQUEST
                requestSerializer != null -> json.decodeFromString(requestSerializer, call.receiveText())
                else -> call.receive()
            }
        val responseBody: RESPONSE = handler(MatrixEndpointContext(endpoint, requestBody, call))
        val responseSerializer = endpoint.responseSerializerBuilder(mappings, json)
        when {
            responseSerializer != null -> call.respond(
                HttpStatusCode.OK,
                json.encodeToJsonElement(responseSerializer, responseBody)
            )
            responseBody == null -> call.respond(HttpStatusCode.OK)
            else -> call.respond(HttpStatusCode.OK, responseBody)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> Route.matrixEndpointResource(
    crossinline block: suspend PipelineContext<Unit, ApplicationCall>.(ENDPOINT) -> Unit
) {
    resource<ENDPOINT> {
        val annotations = serializer<ENDPOINT>().descriptor.annotations
        val endpointHttpMethod = annotations.filterIsInstance<HttpMethod>().firstOrNull()
            ?: throw IllegalArgumentException("matrix endpoint needs @Method annotation")
        val withoutAuth = annotations.filterIsInstance<WithoutAuth>().firstOrNull() != null
        method(io.ktor.http.HttpMethod(endpointHttpMethod.type.name)) {
            intercept(ApplicationCallPipeline.Plugins) {
                call.attributes.put(withoutAuthAttributeKey, withoutAuth)
            }
            handle<ENDPOINT> { endpoint ->
                block(endpoint)
            }
        }
    }
}

@JvmName("matrixEndpointWithUnit")
inline fun <reified ENDPOINT : MatrixEndpoint<Unit, Unit>> Route.matrixEndpoint(
    json: Json,
    mappings: EventContentSerializerMappings,
    crossinline handler: suspend (MatrixEndpointContext<ENDPOINT, Unit, Unit>) -> Unit
) = matrixEndpoint<ENDPOINT, Unit, Unit>(json, mappings, handler = handler)

@JvmName("matrixEndpointWithUnitResponse")
inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, Unit>, reified REQUEST> Route.matrixEndpoint(
    json: Json,
    mappings: EventContentSerializerMappings,
    crossinline handler: suspend (MatrixEndpointContext<ENDPOINT, REQUEST, Unit>) -> Unit
) = matrixEndpoint<ENDPOINT, REQUEST, Unit>(
    json,
    mappings,
    handler = handler
)

@JvmName("matrixEndpointWithUnitRequest")
inline fun <reified ENDPOINT : MatrixEndpoint<Unit, RESPONSE>, reified RESPONSE> Route.matrixEndpoint(
    json: Json,
    mappings: EventContentSerializerMappings,
    crossinline handler: suspend (MatrixEndpointContext<ENDPOINT, Unit, RESPONSE>) -> RESPONSE
) = matrixEndpoint<ENDPOINT, Unit, RESPONSE>(
    json,
    mappings,
    handler = handler
)