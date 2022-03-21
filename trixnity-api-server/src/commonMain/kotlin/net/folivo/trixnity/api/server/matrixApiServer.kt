package net.folivo.trixnity.api.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.util.logging.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.ErrorResponseSerializer
import net.folivo.trixnity.core.MatrixServerException

fun Application.matrixApiServer(json: Json, block: Application.() -> Unit) {
    install(Resources)
    install(ContentNegotiation) {
        json(json)
    }
    install(StatusPages) {
        exception { call: ApplicationCall, cause: Throwable ->
            call.application.log.error(cause)
            when (cause) {
                is MatrixServerException ->
                    call.respond(
                        cause.statusCode,
                        json.encodeToJsonElement(ErrorResponseSerializer, cause.errorResponse)
                    )
                is SerializationException ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        json.encodeToJsonElement(ErrorResponseSerializer, ErrorResponse.BadJson(cause.message))
                    )
                else -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        json.encodeToJsonElement(ErrorResponseSerializer, ErrorResponse.Unknown(cause.message))
                    )
                }
            }
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                json.encodeToJsonElement(ErrorResponseSerializer, ErrorResponse.NotFound())
            )
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respond(
                HttpStatusCode.MethodNotAllowed,
                json.encodeToJsonElement(
                    ErrorResponseSerializer,
                    ErrorResponse.Unrecognized("http request method not allowed")
                )
            )
        }
        status(HttpStatusCode.UnsupportedMediaType) { call, _ ->
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                json.encodeToJsonElement(
                    ErrorResponseSerializer,
                    ErrorResponse.Unrecognized("media type of request is not supported")
                )
            )
        }
    }
    block()
}