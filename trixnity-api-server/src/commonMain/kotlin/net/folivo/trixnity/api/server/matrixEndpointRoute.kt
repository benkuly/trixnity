package net.folivo.trixnity.api.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.MatrixEndpoint

class MatrixEndpointHandler<ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, REQUEST, RESPONSE>(
    val endpoint: ENDPOINT,
    val requestBody: REQUEST,
    val request: ApplicationRequest,
)

// TODO inject json when ktor 2.0.0 is released
inline fun <reified ENDPOINT : MatrixEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> Route.matrixEndpoint(
    json: Json,
    requestSerializer: KSerializer<REQUEST>? = null,
    responseSerializer: KSerializer<RESPONSE>? = null,
    crossinline handler: suspend MatrixEndpointHandler<ENDPOINT, REQUEST, RESPONSE>.() -> RESPONSE
) {
    resource<ENDPOINT> {
        handle<ENDPOINT> { endpoint ->
            if (endpoint.method != this.context.request.httpMethod) return@handle
            val requestBody: REQUEST =
                when {
                    REQUEST::class == Unit::class -> Unit as REQUEST
                    requestSerializer != null -> json.decodeFromString(requestSerializer, call.receiveText())
                    else -> call.receive()
                }
            val responseBody: RESPONSE =
                MatrixEndpointHandler(endpoint, requestBody, call.request).run { handler() }
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
}