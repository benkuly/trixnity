package net.folivo.trixnity.appservice.rest

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import net.folivo.trixnity.client.api.ErrorResponse
import net.folivo.trixnity.client.api.MatrixServerException
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.event.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

fun Application.matrixAppserviceModule(
    properties: MatrixAppserviceProperties,
    appserviceService: AppserviceService,
    loggerFactory: LoggerFactory = LoggerFactory.default,
    customMappings: EventContentSerializerMappings? = null
) {
    val log = newLogger(loggerFactory)
    install(ContentNegotiation) {
        json(createMatrixJson(loggerFactory, DefaultEventContentSerializerMappings + customMappings))
    }
    install(Authentication) {
        matrixQueryParameter("default", "access_token", properties.hsToken)
    }
    install(StatusPages) {
        exception<Throwable> { cause ->
            log.error(cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("M_UNKNOWN", cause.message))
        }
        exception<MatrixServerException> { cause ->
            call.respond(cause.statusCode, cause.errorResponse)
        }
    }

    install(Routing) {
        authenticate("default") {
            controller(appserviceService)
        }
    }
}