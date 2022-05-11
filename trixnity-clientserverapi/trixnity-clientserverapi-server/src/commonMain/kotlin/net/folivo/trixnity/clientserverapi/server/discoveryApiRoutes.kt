package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.clientserverapi.model.discovery.GetWellKnown
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.discoveryApiRoutes(
    handler: DiscoveryApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    authenticate {
        matrixEndpoint<GetWellKnown, DiscoveryInformation>(json, contentMappings) {
            handler.getWellKnown(this)
        }
    }
}