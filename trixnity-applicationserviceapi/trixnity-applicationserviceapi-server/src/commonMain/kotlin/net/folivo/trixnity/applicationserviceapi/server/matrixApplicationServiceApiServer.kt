package net.folivo.trixnity.applicationserviceapi.server

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.applicationserviceapi.model.*
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

fun Application.matrixApplicationServiceApiServer(
    hsToken: String,
    applicationServiceApiServerHandler: ApplicationServiceApiServerHandler,
    customMappings: EventContentSerializerMappings? = null
) {
    val json = createMatrixJson(DefaultEventContentSerializerMappings + customMappings)
    matrixApiServer(json) {
        install(Authentication) {
            matrixQueryParameter("default", "access_token", hsToken)
        }
        routing {
            authenticate("default") {
                matrixEndpoint<AddTransaction, AddTransaction.Request, Unit>(json) {
                    applicationServiceApiServerHandler.addTransaction(endpoint.txnId, requestBody.events)
                }
                matrixEndpoint<AddTransactionLegacy, AddTransactionLegacy.Request, Unit>(json) {
                    applicationServiceApiServerHandler.addTransaction(endpoint.txnId, requestBody.events)
                }
                matrixEndpoint<HasUser, Unit, Unit>(json) {
                    val userId = endpoint.userId
                    val hasUser = applicationServiceApiServerHandler.hasUser(userId)
                    if (!hasUser) throw MatrixNotFoundException("user $userId not found")
                }
                matrixEndpoint<HasUserLegacy, Unit, Unit>(json) {
                    val userId = endpoint.userId
                    val hasUser = applicationServiceApiServerHandler.hasUser(userId)
                    if (!hasUser) throw MatrixNotFoundException("user $userId not found")
                }
                matrixEndpoint<HasRoom, Unit, Unit>(json) {
                    val roomAlias = endpoint.roomAlias
                    val hasRoom = applicationServiceApiServerHandler.hasRoomAlias(roomAlias)
                    if (!hasRoom) throw MatrixNotFoundException("room $roomAlias not found")
                }
                matrixEndpoint<HasRoomLegacy, Unit, Unit>(json) {
                    val roomAlias = endpoint.roomAlias
                    val hasRoom = applicationServiceApiServerHandler.hasRoomAlias(roomAlias)
                    if (!hasRoom) throw MatrixNotFoundException("room $roomAlias not found")
                }
            }
        }
    }
}