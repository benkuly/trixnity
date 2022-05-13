package net.folivo.trixnity.applicationserviceapi.server

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.applicationserviceapi.model.*
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

fun Application.matrixApplicationServiceApiServer(
    hsToken: String,
    applicationServiceApiServerHandler: ApplicationServiceApiServerHandler,
    customMappings: EventContentSerializerMappings? = null
) {
    val contentMappings = DefaultEventContentSerializerMappings + customMappings
    val json = createMatrixEventJson(contentMappings)
    matrixApiServer(json) {
        install(Authentication) {
            matrixQueryParameter(null, "access_token", hsToken)
        }
        routing {
            authenticate {
                matrixEndpoint<AddTransaction, AddTransaction.Request, Unit>(json, contentMappings) {
                    applicationServiceApiServerHandler.addTransaction(endpoint.txnId, requestBody.events)
                }
                matrixEndpoint<AddTransactionLegacy, AddTransactionLegacy.Request, Unit>(json, contentMappings) {
                    applicationServiceApiServerHandler.addTransaction(endpoint.txnId, requestBody.events)
                }
                matrixEndpoint<HasUser, Unit, Unit>(json, contentMappings) {
                    applicationServiceApiServerHandler.hasUser(endpoint.userId)
                }
                matrixEndpoint<HasUserLegacy, Unit, Unit>(json, contentMappings) {
                    applicationServiceApiServerHandler.hasUser(endpoint.userId)
                }
                matrixEndpoint<HasRoom, Unit, Unit>(json, contentMappings) {
                    applicationServiceApiServerHandler.hasRoomAlias(endpoint.roomAlias)
                }
                matrixEndpoint<HasRoomLegacy, Unit, Unit>(json, contentMappings) {
                    applicationServiceApiServerHandler.hasRoomAlias(endpoint.roomAlias)

                }
            }
        }
    }
}