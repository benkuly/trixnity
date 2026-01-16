package de.connect2x.trixnity.api.server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.core.ErrorResponse
import de.connect2x.trixnity.core.MatrixServerException

fun Application.matrixApiServer(json: Json, routes: Route.() -> Unit) {
    installMatrixApiServer(json)
    routing {
        routes()
    }
}

fun Application.installMatrixApiServer(json: Json) {
    install(Resources)
    install(StatusPages) {
        exception { call: ApplicationCall, cause: Throwable ->
            call.application.log.error(cause)
            when (cause) {
                is MatrixServerException -> {
                    val retryAfter = cause.retryAfter
                    if (retryAfter != null) call.response.header(HttpHeaders.RetryAfter, retryAfter)
                    val errorResponse = cause.errorResponse.let {
                        if (it is ErrorResponse.LimitExceeded && retryAfter != null)
                            it.copy(retryAfterMillis = retryAfter * 1000)
                        else it
                    }
                    call.respond(
                        cause.statusCode,
                        json.encodeToJsonElement(ErrorResponse.Serializer, errorResponse)
                    )
                }

                is SerializationException ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        json.encodeToJsonElement(
                            ErrorResponse.Serializer,
                            ErrorResponse.BadJson(cause.message ?: "unknown")
                        )
                    )

                else -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        json.encodeToJsonElement(
                            ErrorResponse.Serializer,
                            ErrorResponse.Unknown(cause.message ?: "unknown")
                        )
                    )
                }
            }
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                json.encodeToJsonElement(
                    ErrorResponse.Serializer,
                    ErrorResponse.Unrecognized("unsupported (or unknown) endpoint")
                )
            )
        }
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
            call.respond(
                HttpStatusCode.MethodNotAllowed,
                json.encodeToJsonElement(
                    ErrorResponse.Serializer,
                    ErrorResponse.Unrecognized("http request method not allowed")
                )
            )
        }
        status(HttpStatusCode.UnsupportedMediaType) { call, _ ->
            call.respond(
                HttpStatusCode.UnsupportedMediaType,
                json.encodeToJsonElement(
                    ErrorResponse.Serializer,
                    ErrorResponse.Unrecognized("media type of request is not supported")
                )
            )
        }
    }
    install(ContentNegotiation) {
        json(json)
    }
}