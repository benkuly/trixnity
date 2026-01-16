package de.connect2x.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default

fun Route.matrixClientServerApiServerRoutes(
    appserviceApiHandler: AppserviceApiHandler,
    authenticationApiHandler: AuthenticationApiHandler,
    deviceApiHandler: DeviceApiHandler,
    discoveryApiHandler: DiscoveryApiHandler,
    keyApiHandler: KeyApiHandler,
    mediaApiHandler: MediaApiHandler,
    pushApiHandler: PushApiHandler,
    roomApiHandler: RoomApiHandler,
    serverApiHandler: ServerApiHandler,
    syncApiHandler: SyncApiHandler,
    userApiHandler: UserApiHandler,
    eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.default,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
) {
    appserviceApiRoutes(appserviceApiHandler, json, eventContentSerializerMappings)
    authenticationApiRoutes(authenticationApiHandler, json, eventContentSerializerMappings)
    deviceApiRoutes(deviceApiHandler, json, eventContentSerializerMappings)
    discoveryApiRoutes(discoveryApiHandler, json, eventContentSerializerMappings)
    keyApiRoutes(keyApiHandler, json, eventContentSerializerMappings)
    mediaApiRoutes(mediaApiHandler, json, eventContentSerializerMappings)
    pushApiRoutes(pushApiHandler, json, eventContentSerializerMappings)
    roomApiRoutes(roomApiHandler, json, eventContentSerializerMappings)
    serverApiRoutes(serverApiHandler, json, eventContentSerializerMappings)
    syncApiRoutes(syncApiHandler, json, eventContentSerializerMappings)
    userApiRoutes(userApiHandler, json, eventContentSerializerMappings)
}