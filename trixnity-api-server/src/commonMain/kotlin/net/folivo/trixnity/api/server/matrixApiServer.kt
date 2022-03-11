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
import net.folivo.trixnity.core.serialization.createMatrixJson

fun Application.matrixApiServer(json: Json = createMatrixJson(), block: Application.() -> Unit) {
    install(Resources)
    install(ContentNegotiation) {
        json(json)
    }
    install(StatusPages) {
        exception { call: ApplicationCall, cause: Throwable ->
            log.error(cause)
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
    }
    block()
}