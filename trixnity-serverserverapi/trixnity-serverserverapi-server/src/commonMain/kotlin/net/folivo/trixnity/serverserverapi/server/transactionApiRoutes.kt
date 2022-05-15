package net.folivo.trixnity.serverserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.serverserverapi.model.transaction.GetEventAuthChain
import net.folivo.trixnity.serverserverapi.model.transaction.SendTransaction

internal fun Route.transactionApiRoutes(
    handler: TransactionApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    authenticate {
        matrixEndpoint<SendTransaction, SendTransaction.Request, SendTransaction.Response>(json, contentMappings) {
            handler.sendTransaction(this)
        }
        matrixEndpoint<GetEventAuthChain, GetEventAuthChain.Response>(json, contentMappings) {
            handler.getEventAuthChain(this)
        }
    }
}