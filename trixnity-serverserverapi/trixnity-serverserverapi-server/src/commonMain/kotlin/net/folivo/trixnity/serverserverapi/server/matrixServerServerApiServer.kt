package net.folivo.trixnity.serverserverapi.server

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.RoomVersionStore
import net.folivo.trixnity.core.serialization.events.default

fun Application.matrixServerServerApiServer(
    hostname: String,
    signatureAuthenticationFunction: SignatureAuthenticationFunction,
    roomVersionStore: RoomVersionStore,
    eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.default,
    json: Json = createMatrixEventAndDataUnitJson(roomVersionStore, eventContentSerializerMappings),
    routes: Route.() -> Unit,
) {
    installMatrixSignatureAuth("matrix-signature-auth", hostname = hostname) {
        this.authenticationFunction = signatureAuthenticationFunction
    }
    install(ConvertMediaPlugin)
    matrixApiServer(json) {
        authenticate("matrix-signature-auth") {
            routes()
        }
    }
}