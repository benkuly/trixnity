package net.folivo.trixnity.appservice.rest

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import net.folivo.trixnity.appservice.AppserviceService
import net.folivo.trixnity.appservice.MatrixAppserviceProperties
import net.folivo.trixnity.appservice.matrixQueryParameter
import net.folivo.trixnity.clientserverapi.client.MatrixServerException
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

fun Application.matrixAppserviceModule(
    properties: MatrixAppserviceProperties,
    appserviceService: AppserviceService,
    customMappings: EventContentSerializerMappings? = null
) {
    val json = createMatrixJson(DefaultEventContentSerializerMappings + customMappings)
    install(ContentNegotiation) {
        json(json)
    }
    install(Authentication) {
        matrixQueryParameter("default", "access_token", properties.hsToken)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is MatrixServerException -> call.respond(
                    cause.statusCode,
                    json.encodeToJsonElement(
                        net.folivo.trixnity.clientserverapi.model.ErrorResponseSerializer,
                        cause.errorResponse
                    )
                )
                else -> {
                    log.error(cause)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        net.folivo.trixnity.clientserverapi.model.ErrorResponse.Unknown(cause.message)
                    )
                }
            }
        }
    }

    install(Routing) {
        authenticate("default") {
            controller(appserviceService)
        }
    }
}