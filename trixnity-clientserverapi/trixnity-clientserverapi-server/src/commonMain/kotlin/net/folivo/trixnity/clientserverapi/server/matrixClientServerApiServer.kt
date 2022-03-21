package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

fun Application.matrixClientServerApiServer(
    accessTokenAuthenticationFunction: AccessTokenAuthenticationFunction,
    authenticationApiHandler: AuthenticationApiHandler,
    devicesApiHandler: DevicesApiHandler,
    keysApiHandler: KeysApiHandler,
    mediaApiHandler: MediaApiHandler,
    pushApiHandler: PushApiHandler,
    roomsApiHandler: RoomsApiHandler,
    serverApiHandler: ServerApiHandler,
    syncApiHandler: SyncApiHandler,
    usersApiHandler: UsersApiHandler,
    customMappings: EventContentSerializerMappings? = null,
) {
    val contentMappings = createEventContentSerializerMappings(customMappings)
    val json = createMatrixJson(contentMappings)
    matrixApiServer(json) {
        // TODO rate limit
        install(ConvertMediaPlugin)
        install(Authentication) {
            matrixAccessTokenAuth {
                this.authenticationFunction = accessTokenAuthenticationFunction
            }
        }
        routing {
            authenticationApiRoutes(authenticationApiHandler, json, contentMappings)
            devicesApiRoutes(devicesApiHandler, json, contentMappings)
            keysApiRoutes(keysApiHandler, json, contentMappings)
            mediaApiRoutes(mediaApiHandler, json, contentMappings)
            pushApiRoutes(pushApiHandler, json, contentMappings)
            roomsApiRoutes(roomsApiHandler, json, contentMappings)
            serverApiRoutes(serverApiHandler, json, contentMappings)
            syncApiRoutes(syncApiHandler, json, contentMappings)
            usersApiRoutes(usersApiHandler, json, contentMappings)
        }
    }
}