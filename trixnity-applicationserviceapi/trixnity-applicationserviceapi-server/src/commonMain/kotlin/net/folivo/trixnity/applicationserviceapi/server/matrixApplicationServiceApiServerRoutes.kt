package net.folivo.trixnity.applicationserviceapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.applicationserviceapi.model.*
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

fun Route.matrixApplicationServiceApiServerRoutes(
    applicationServiceApiServerHandler: ApplicationServiceApiServerHandler,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
) {
    matrixEndpoint<AddTransaction, AddTransaction.Request, Unit>(json, eventContentSerializerMappings) {
        applicationServiceApiServerHandler.addTransaction(endpoint.txnId, requestBody.events)
    }
    matrixEndpoint<AddTransactionLegacy, AddTransactionLegacy.Request, Unit>(json, eventContentSerializerMappings) {
        applicationServiceApiServerHandler.addTransaction(endpoint.txnId, requestBody.events)
    }
    matrixEndpoint<HasUser, Unit, Unit>(json, eventContentSerializerMappings) {
        applicationServiceApiServerHandler.hasUser(endpoint.userId)
    }
    matrixEndpoint<HasUserLegacy, Unit, Unit>(json, eventContentSerializerMappings) {
        applicationServiceApiServerHandler.hasUser(endpoint.userId)
    }
    matrixEndpoint<HasRoom, Unit, Unit>(json, eventContentSerializerMappings) {
        applicationServiceApiServerHandler.hasRoomAlias(endpoint.roomAlias)
    }
    matrixEndpoint<HasRoomLegacy, Unit, Unit>(json, eventContentSerializerMappings) {
        applicationServiceApiServerHandler.hasRoomAlias(endpoint.roomAlias)
    }
}