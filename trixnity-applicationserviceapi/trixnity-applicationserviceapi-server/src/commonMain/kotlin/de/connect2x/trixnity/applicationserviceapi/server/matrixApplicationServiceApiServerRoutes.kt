package de.connect2x.trixnity.applicationserviceapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.api.server.matrixEndpoint
import de.connect2x.trixnity.applicationserviceapi.model.*
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default

fun Route.matrixApplicationServiceApiServerRoutes(
    applicationServiceApiServerHandler: ApplicationServiceApiServerHandler,
    eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.default,
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
    matrixEndpoint<Ping, Ping.Request, Unit>(json, eventContentSerializerMappings) {
        applicationServiceApiServerHandler.ping(requestBody.txnId)
    }
}