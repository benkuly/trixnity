package net.folivo.trixnity.appservice.rest

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.flow.asFlow
import net.folivo.trixnity.appservice.rest.api.AppserviceHandler
import net.folivo.trixnity.appservice.rest.api.MatrixBadRequestException
import net.folivo.trixnity.appservice.rest.api.MatrixNotFoundException
import net.folivo.trixnity.appservice.rest.api.event.EventRequest
import net.folivo.trixnity.client.rest.api.MatrixServerException
import net.folivo.trixnity.core.model.MatrixId

fun Route.controller(appserviceHandler: AppserviceHandler) {
    v1(appserviceHandler)
    route("/_matrix/app/v1") {
        v1(appserviceHandler)
    }
}

fun Route.v1(appserviceHandler: AppserviceHandler) {
    /**
     * @see <a href="https://matrix.org/docs/spec/application_service/r0.1.2#put-matrix-app-v1-transactions-txnid">matrix spec</a>
     */
    put("/transactions/{tnxId}") {
        val tnxId = call.parameters["tnxId"] ?: throw MatrixBadRequestException("txnId path parameter was missing")
        val eventRequest = call.receive<EventRequest>()

        appserviceHandler.addTransactions(tnxId, eventRequest.events.asFlow())

        call.respond(HttpStatusCode.OK, "{}")
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/application_service/r0.1.2#get-matrix-app-v1-users-userid">matrix spec</a>
     */
    get("/users/{userId}") {
        val userId = call.parameters["userId"] ?: throw MatrixBadRequestException("userId path parameter was missing")

        try {
            val hasUser = appserviceHandler.hasUser(MatrixId.UserId(userId))
            if (hasUser) call.respond(
                HttpStatusCode.OK,
                "{}"
            ) else throw MatrixNotFoundException("user $userId not found")
        } catch (error: Throwable) {
            if (error !is MatrixServerException) {
                throw MatrixNotFoundException(error.message ?: "unknown")
            } else {
                throw error
            }
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/application_service/r0.1.2#get-matrix-app-v1-rooms-roomalias">matrix spec</a>
     */
    get("/rooms/{roomAlias}") {
        val roomAlias =
            call.parameters["roomAlias"] ?: throw MatrixBadRequestException("roomAlias path parameter was missing")
        try {
            val hasRoomAlias = appserviceHandler.hasRoomAlias(MatrixId.RoomAliasId(roomAlias))
            if (hasRoomAlias) call.respond(
                HttpStatusCode.OK,
                "{}"
            ) else throw MatrixNotFoundException("no room alias $roomAlias found")
        } catch (error: Throwable) {
            if (error !is MatrixServerException) {
                throw MatrixNotFoundException(error.message ?: "unknown")
            } else {
                throw error
            }
        }
    }
}