package net.folivo.trixnity.appservice.rest

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import net.folivo.trixnity.appservice.rest.api.AppserviceHandler
import net.folivo.trixnity.client.rest.api.ErrorResponse
import net.folivo.trixnity.client.rest.api.MatrixServerException
import net.folivo.trixnity.core.model.events.RoomEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.serialization.createJson
import net.folivo.trixnity.core.serialization.event.DEFAULT_ROOM_EVENT_CONTENT_SERIALIZERS
import net.folivo.trixnity.core.serialization.event.DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMapping

fun Application.appserviceModule(
    properties: AppserviceProperties,
    appserviceHandler: AppserviceHandler,
    customRoomEventContentSerializers: Set<EventContentSerializerMapping<out RoomEventContent>> = emptySet(),
    customStateEventContentSerializers: Set<EventContentSerializerMapping<out StateEventContent>> = emptySet()
) {
    install(ContentNegotiation) {
        json(
            createJson(
                DEFAULT_ROOM_EVENT_CONTENT_SERIALIZERS + customRoomEventContentSerializers,
                DEFAULT_STATE_EVENT_CONTENT_SERIALIZERS + customStateEventContentSerializers
            )
        )
    }
    install(Authentication) {
        matrixQueryParameter("default", "access_token", properties.hsToken)
    }
    install(StatusPages) {
        exception<Throwable> { cause ->
            println(cause.stackTraceToString()) // TODO use logger
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("M_UNKNOWN", cause.message))
        }
        exception<MatrixServerException> { cause ->
            call.respond(cause.statusCode, cause.errorResponse)
        }
    }

    install(Routing) {
        authenticate("default") {
            controller(appserviceHandler)
        }
    }
}