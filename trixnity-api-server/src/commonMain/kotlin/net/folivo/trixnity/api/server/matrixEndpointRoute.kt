package net.folivo.trixnity.api.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.folivo.trixnity.core.Auth
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.jvm.JvmName

class MatrixEndpointContext<ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, REQUEST, RESPONSE>(
    val endpoint: ENDPOINT,
    val requestBody: REQUEST,
    val call: ApplicationCall,
)

// TODO inject json and mappings with context receivers in a future kotlin version
inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> Route.matrixEndpoint(
    json: Json,
    mappings: EventContentSerializerMappings,
    crossinline handler: suspend MatrixEndpointContext<ENDPOINT, REQUEST, RESPONSE>.() -> RESPONSE,
) = matrixEndpointResource<ENDPOINT> { endpoint ->
    val requestSerializer: KSerializer<REQUEST>? = endpoint.requestSerializerBuilder(mappings, json, null)
    val requestBody: REQUEST =
        when {
            REQUEST::class == Unit::class -> Unit as REQUEST
            requestSerializer != null -> json.decodeFromJsonElement(requestSerializer, call.receive())
            else -> call.receive()
        }
    call.response.status(HttpStatusCode.OK)
    val responseBody: RESPONSE = handler(MatrixEndpointContext(endpoint, requestBody, call))
    val responseSerializer = endpoint.responseSerializerBuilder(mappings, json, responseBody)
    when {
        responseSerializer != null -> call.respond(
            json.encodeToJsonElement(responseSerializer, responseBody)
        )

        responseBody == null -> {}
        else -> call.respond(responseBody)
    }
}

@JvmName("matrixEndpointWithUnit")
inline fun <reified ENDPOINT : MatrixEndpoint<Unit, Unit>> Route.matrixEndpoint(
    json: Json,
    mappings: EventContentSerializerMappings,
    crossinline handler: suspend MatrixEndpointContext<ENDPOINT, Unit, Unit>.() -> Unit
) = matrixEndpoint<ENDPOINT, Unit, Unit>(json, mappings, handler = handler)

@JvmName("matrixEndpointWithUnitResponse")
inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, Unit>, reified REQUEST> Route.matrixEndpoint(
    json: Json,
    mappings: EventContentSerializerMappings,
    crossinline handler: suspend MatrixEndpointContext<ENDPOINT, REQUEST, Unit>.() -> Unit
) = matrixEndpoint<ENDPOINT, REQUEST, Unit>(
    json,
    mappings,
    handler = handler
)

@JvmName("matrixEndpointWithUnitRequest")
inline fun <reified ENDPOINT : MatrixEndpoint<Unit, RESPONSE>, reified RESPONSE> Route.matrixEndpoint(
    json: Json,
    mappings: EventContentSerializerMappings,
    crossinline handler: suspend MatrixEndpointContext<ENDPOINT, Unit, RESPONSE>.() -> RESPONSE
) = matrixEndpoint<ENDPOINT, Unit, RESPONSE>(
    json,
    mappings,
    handler = handler
)

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified ENDPOINT : MatrixEndpoint<*, *>> Route.matrixEndpointResource(
    crossinline block: suspend RoutingContext.(ENDPOINT) -> Unit
) = resource<ENDPOINT> {
    val annotations = serializer<ENDPOINT>().descriptor.annotations
    val endpointHttpMethod = annotations.filterIsInstance<HttpMethod>().firstOrNull()
        ?: throw IllegalArgumentException("matrix endpoint needs @Method annotation")
    val authRequired =
        annotations.filterIsInstance<Auth>().firstOrNull()?.required ?: AuthRequired.YES
    method(io.ktor.http.HttpMethod(endpointHttpMethod.type.name)) {
        install(createRouteScopedPlugin("AuthAttributeKeyPlugin") {
            on(CallSetup) { call ->
                call.attributes.put(AuthRequired.attributeKey, authRequired)
            }
        })
        handle<ENDPOINT> { endpoint ->
            block(endpoint)
        }
    }
}