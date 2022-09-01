package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

fun Routing.matrixClientServerApiServerRoutes(
    discoveryApiHandler: DiscoveryApiHandler,
    authenticationApiHandler: AuthenticationApiHandler,
    devicesApiHandler: DevicesApiHandler,
    keysApiHandler: KeysApiHandler,
    mediaApiHandler: MediaApiHandler,
    pushApiHandler: PushApiHandler,
    roomsApiHandler: RoomsApiHandler,
    serverApiHandler: ServerApiHandler,
    syncApiHandler: SyncApiHandler,
    usersApiHandler: UsersApiHandler,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
) {
    discoveryApiRoutes(discoveryApiHandler, json, eventContentSerializerMappings)
    authenticationApiRoutes(authenticationApiHandler, json, eventContentSerializerMappings)
    devicesApiRoutes(devicesApiHandler, json, eventContentSerializerMappings)
    keysApiRoutes(keysApiHandler, json, eventContentSerializerMappings)
    mediaApiRoutes(mediaApiHandler, json, eventContentSerializerMappings)
    pushApiRoutes(pushApiHandler, json, eventContentSerializerMappings)
    roomsApiRoutes(roomsApiHandler, json, eventContentSerializerMappings)
    serverApiRoutes(serverApiHandler, json, eventContentSerializerMappings)
    syncApiRoutes(syncApiHandler, json, eventContentSerializerMappings)
    usersApiRoutes(usersApiHandler, json, eventContentSerializerMappings)
}