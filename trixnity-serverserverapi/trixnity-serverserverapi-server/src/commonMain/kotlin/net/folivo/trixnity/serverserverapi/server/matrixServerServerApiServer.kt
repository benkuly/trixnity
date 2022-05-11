package net.folivo.trixnity.serverserverapi.server

import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.routing.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

fun Application.matrixServerServerApiServer(
    hostname: String,
    signatureAuthenticationFunction: SignatureAuthenticationFunction,
    discoveryApiHandler: DiscoveryApiHandler,
    customMappings: EventContentSerializerMappings? = null,
) {
    val contentMappings = createEventContentSerializerMappings(customMappings)
    val json = createMatrixJson(contentMappings)
    matrixApiServer(json) {
        install(DoubleReceive)
        installMatrixSignatureAuth(hostname = hostname) {
            this.authenticationFunction = signatureAuthenticationFunction
        }
        routing {
            discoveryApiRoutes(discoveryApiHandler, json, contentMappings)
        }
    }
}