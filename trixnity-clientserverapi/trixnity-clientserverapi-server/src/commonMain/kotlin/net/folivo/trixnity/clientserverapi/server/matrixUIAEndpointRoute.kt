package net.folivo.trixnity.clientserverapi.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.api.server.matrixEndpointResource
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.RequestWithUIASerializer
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA.*
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

// TODO inject json and mappings with context receivers with kotlin > 1.7.0
inline fun <reified ENDPOINT : MatrixUIAEndpoint<REQUEST, RESPONSE>, reified REQUEST, reified RESPONSE> Route.matrixUIAEndpoint(
    json: Json,
    mappings: EventContentSerializerMappings,
    crossinline handler: suspend (MatrixEndpointContext<ENDPOINT, RequestWithUIA<REQUEST>, ResponseWithUIA<RESPONSE>>) -> ResponseWithUIA<RESPONSE>
) {
    matrixEndpointResource<ENDPOINT, RequestWithUIA<REQUEST>, ResponseWithUIA<RESPONSE>> { endpoint ->
        val requestSerializer: KSerializer<REQUEST>? = endpoint.plainRequestSerializerBuilder(mappings, json)
        val requestBody: RequestWithUIA<REQUEST> =
            when {
                requestSerializer != null -> json.decodeFromJsonElement(
                    RequestWithUIASerializer(requestSerializer),
                    call.receive()
                )
                else -> call.receive()
            }
        val responseBody: ResponseWithUIA<RESPONSE> = handler(MatrixEndpointContext(endpoint, requestBody, call))
        val responseSerializer: KSerializer<RESPONSE>? = endpoint.plainResponseSerializerBuilder(mappings, json)
        when (responseBody) {
            is Success -> {
                val responseValue = responseBody.value
                when {
                    responseSerializer != null -> call.respond(
                        HttpStatusCode.OK,
                        json.encodeToJsonElement(responseSerializer, responseValue)
                    )
                    responseValue == null -> call.respond(HttpStatusCode.OK)
                    else -> call.respond(HttpStatusCode.OK, responseValue)
                }
            }
            is Step ->
                call.respond(HttpStatusCode.Unauthorized, json.encodeToJsonElement(responseBody.state))
            is Error ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    JsonObject(buildMap {
                        putAll(json.encodeToJsonElement(responseBody.state).jsonObject)
                        putAll(json.encodeToJsonElement(responseBody.errorResponse).jsonObject)
                    })
                )
        }
    }
}