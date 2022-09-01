package net.folivo.trixnity.serverserverapi.server

import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.GetRoomVersionFunction

fun Application.matrixServerServerApiServer(
    hostname: String,
    signatureAuthenticationFunction: SignatureAuthenticationFunction,
    getRoomVersionFunction: GetRoomVersionFunction,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    json: Json = createMatrixEventAndDataUnitJson(getRoomVersionFunction, eventContentSerializerMappings),
    block: Application.() -> Unit,
) {
    matrixApiServer(json) {
        install(DoubleReceive)
        installMatrixSignatureAuth(hostname = hostname) {
            this.authenticationFunction = signatureAuthenticationFunction
        }
        block()
    }
}