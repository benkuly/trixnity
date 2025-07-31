package net.folivo.trixnity.serverserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.RoomVersionStore

fun Route.matrixServerServerApiServerRoutes(
    discoveryApiHandler: DiscoveryApiHandler,
    federationApiHandler: FederationApiHandler,
    roomVersionStore: RoomVersionStore,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    json: Json = createMatrixEventAndDataUnitJson(roomVersionStore, eventContentSerializerMappings),
) {
    discoveryApiRoutes(discoveryApiHandler, json, eventContentSerializerMappings)
    federationApiRoutes(federationApiHandler, json, eventContentSerializerMappings)
}