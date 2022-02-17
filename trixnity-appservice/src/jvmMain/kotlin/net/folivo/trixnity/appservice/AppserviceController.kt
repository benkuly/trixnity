package net.folivo.trixnity.appservice.rest

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.asFlow
import net.folivo.trixnity.appservice.AppserviceService
import net.folivo.trixnity.appservice.MatrixBadRequestException
import net.folivo.trixnity.appservice.MatrixNotFoundException
import net.folivo.trixnity.appservice.event.EventRequest
import net.folivo.trixnity.clientserverapi.client.MatrixServerException
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.UserId

fun Route.controller(appserviceService: AppserviceService) {
    v1(appserviceService)
    route("/_matrix/app/v1") {
        v1(appserviceService)
    }
}

fun Route.v1(appserviceService: AppserviceService) {
    /**
     * @see <a href="https://matrix.org/docs/spec/application_service/r0.1.2#put-matrix-app-v1-transactions-txnid">matrix spec</a>
     */
    put("/transactions/{tnxId}") {
        val tnxId = call.parameters["tnxId"] ?: throw MatrixBadRequestException("txnId path parameter was missing")
        val eventRequest = call.receive<EventRequest>()

        appserviceService.addTransactions(tnxId, eventRequest.events.asFlow())

        call.respond(HttpStatusCode.OK, "{}")
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/application_service/r0.1.2#get-matrix-app-v1-users-userid">matrix spec</a>
     */
    get("/users/{userId}") {
        val userId = call.parameters["userId"] ?: throw MatrixBadRequestException("userId path parameter was missing")

        try {
            val hasUser = appserviceService.hasUser(UserId(userId))
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
            val hasRoomAlias = appserviceService.hasRoomAlias(RoomAliasId(roomAlias))
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