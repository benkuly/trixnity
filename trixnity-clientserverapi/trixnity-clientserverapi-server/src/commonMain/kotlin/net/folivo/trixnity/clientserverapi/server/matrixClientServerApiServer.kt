package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.application.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.serialization.createMatrixJson

suspend fun Application.matrixClientServerApiServer() {
    val json = createMatrixJson()
    matrixApiServer(json) {
        TODO("not implemented yet")
//        routing {
//            matrixEndpoint<GetStateEvent<StateEventContent>, Unit, StateEventContent>(json) {
//                MemberEventContent(membership = Membership.JOIN)
//            }
//        }
    }
}