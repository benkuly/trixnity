package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.default

fun Route.matrixClientServerApiServerRoutes(
    appserviceApiHandler: AppserviceApiHandler,
    authenticationApiHandler: AuthenticationApiHandler,
    devicesApiHandler: DevicesApiHandler,
    discoveryApiHandler: DiscoveryApiHandler,
    keysApiHandler: KeysApiHandler,
    mediaApiHandler: MediaApiHandler,
    pushApiHandler: PushApiHandler,
    roomsApiHandler: RoomsApiHandler,
    serverApiHandler: ServerApiHandler,
    syncApiHandler: SyncApiHandler,
    usersApiHandler: UsersApiHandler,
    eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.default,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
) {
    appserviceApiRoutes(appserviceApiHandler, json, eventContentSerializerMappings)
    authenticationApiRoutes(authenticationApiHandler, json, eventContentSerializerMappings)
    devicesApiRoutes(devicesApiHandler, json, eventContentSerializerMappings)
    discoveryApiRoutes(discoveryApiHandler, json, eventContentSerializerMappings)
    keysApiRoutes(keysApiHandler, json, eventContentSerializerMappings)
    mediaApiRoutes(mediaApiHandler, json, eventContentSerializerMappings)
    pushApiRoutes(pushApiHandler, json, eventContentSerializerMappings)
    roomsApiRoutes(roomsApiHandler, json, eventContentSerializerMappings)
    serverApiRoutes(serverApiHandler, json, eventContentSerializerMappings)
    syncApiRoutes(syncApiHandler, json, eventContentSerializerMappings)
    usersApiRoutes(usersApiHandler, json, eventContentSerializerMappings)
}