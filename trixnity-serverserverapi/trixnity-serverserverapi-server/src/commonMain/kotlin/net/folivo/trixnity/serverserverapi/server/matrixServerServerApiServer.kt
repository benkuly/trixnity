package net.folivo.trixnity.serverserverapi.server

import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.routing.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.serialization.createEphemeralDateUnitContentSerializerMappings
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixDataUnitJson
import net.folivo.trixnity.core.serialization.events.EphemeralDataUnitContentMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.GetRoomVersionFunction

fun Application.matrixServerServerApiServer(
    hostname: String,
    signatureAuthenticationFunction: SignatureAuthenticationFunction,
    getRoomVersion: GetRoomVersionFunction,
    discoveryApiHandler: DiscoveryApiHandler,
    federationApiHandler: FederationApiHandler,
    customMappings: EventContentSerializerMappings? = null,
    customEphemeralMappings: EphemeralDataUnitContentMappings? = null,
) {
    val contentMappings = createEventContentSerializerMappings(customMappings)
    val ephemeralDataUnitContentMappings = createEphemeralDateUnitContentSerializerMappings(customEphemeralMappings)
    val json = createMatrixDataUnitJson(getRoomVersion, contentMappings, ephemeralDataUnitContentMappings)
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