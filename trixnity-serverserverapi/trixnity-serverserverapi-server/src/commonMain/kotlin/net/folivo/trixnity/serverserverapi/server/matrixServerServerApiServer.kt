package net.folivo.trixnity.serverserverapi.server

import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.routing.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixDataUnitJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.GetRoomVersionFunction

fun Application.matrixServerServerApiServer(
    hostname: String,
    signatureAuthenticationFunction: SignatureAuthenticationFunction,
    getRoomVersion: GetRoomVersionFunction,
    discoveryApiHandler: DiscoveryApiHandler,
    federationApiHandler: FederationApiHandler,
    customMappings: EventContentSerializerMappings? = null,
) {
    val contentMappings = createEventContentSerializerMappings(customMappings)
    val json = createMatrixDataUnitJson(getRoomVersion, contentMappings)
    matrixApiServer(json) {
        install(DoubleReceive)
        installMatrixSignatureAuth(hostname = hostname) {
            this.authenticationFunction = signatureAuthenticationFunction
        }
        routing {
            discoveryApiRoutes(discoveryApiHandler, json, contentMappings)
            federationApiRoutes(federationApiHandler, json, contentMappings)
        }
    }
}