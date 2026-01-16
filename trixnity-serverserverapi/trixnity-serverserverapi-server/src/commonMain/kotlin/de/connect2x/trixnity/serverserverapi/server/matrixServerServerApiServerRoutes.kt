package de.connect2x.trixnity.serverserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.RoomVersionStore
import de.connect2x.trixnity.core.serialization.events.default

fun Route.matrixServerServerApiServerRoutes(
    discoveryApiHandler: DiscoveryApiHandler,
    federationApiHandler: FederationApiHandler,
    roomVersionStore: RoomVersionStore,
    eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.default,
    json: Json = createMatrixEventAndDataUnitJson(roomVersionStore, eventContentSerializerMappings),
) {
    discoveryApiRoutes(discoveryApiHandler, json, eventContentSerializerMappings)
    federationApiRoutes(federationApiHandler, json, eventContentSerializerMappings)
}