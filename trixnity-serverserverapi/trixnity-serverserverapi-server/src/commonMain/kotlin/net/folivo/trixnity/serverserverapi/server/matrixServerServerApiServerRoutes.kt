package net.folivo.trixnity.serverserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.GetRoomVersionFunction

fun Routing.matrixServerServerApiServerRoutes(
    discoveryApiHandler: DiscoveryApiHandler,
    federationApiHandler: FederationApiHandler,
    getRoomVersionFunction: GetRoomVersionFunction,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    json: Json = createMatrixEventAndDataUnitJson(getRoomVersionFunction, eventContentSerializerMappings),
) {
    discoveryApiRoutes(discoveryApiHandler, json, eventContentSerializerMappings)
    federationApiRoutes(federationApiHandler, json, eventContentSerializerMappings)
}