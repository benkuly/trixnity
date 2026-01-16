package de.connect2x.trixnity.serverserverapi.server

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.api.server.matrixApiServer
import de.connect2x.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.RoomVersionStore
import de.connect2x.trixnity.core.serialization.events.default

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