package net.folivo.trixnity.applicationserviceapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.applicationserviceapi.model.*
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

fun Routing.matrixApplicationServiceApiServerRoutes(
    applicationServiceApiServerHandler: ApplicationServiceApiServerHandler,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
) {
    authenticate {
        matrixEndpoint<AddTransaction, AddTransaction.Request, Unit>(json, eventContentSerializerMappings) {
            applicationServiceApiServerHandler.addTransaction(it.endpoint.txnId, it.requestBody.events)
        }
        matrixEndpoint<AddTransactionLegacy, AddTransactionLegacy.Request, Unit>(json, eventContentSerializerMappings) {
            applicationServiceApiServerHandler.addTransaction(it.endpoint.txnId, it.requestBody.events)
        }
        matrixEndpoint<HasUser, Unit, Unit>(json, eventContentSerializerMappings) {
            applicationServiceApiServerHandler.hasUser(it.endpoint.userId)
        }
        matrixEndpoint<HasUserLegacy, Unit, Unit>(json, eventContentSerializerMappings) {
            applicationServiceApiServerHandler.hasUser(it.endpoint.userId)
        }
        matrixEndpoint<HasRoom, Unit, Unit>(json, eventContentSerializerMappings) {
            applicationServiceApiServerHandler.hasRoomAlias(it.endpoint.roomAlias)
        }
        matrixEndpoint<HasRoomLegacy, Unit, Unit>(json, eventContentSerializerMappings) {
            applicationServiceApiServerHandler.hasRoomAlias(it.endpoint.roomAlias)

        }
    }
}