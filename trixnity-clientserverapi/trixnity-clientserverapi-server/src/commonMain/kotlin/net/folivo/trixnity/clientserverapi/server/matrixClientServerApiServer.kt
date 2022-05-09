package net.folivo.trixnity.clientserverapi.server

import io.ktor.http.*
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
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
        // see also https://spec.matrix.org/v1.2/client-server-api/#web-browser-clients
        install(CORS) {
            anyHost()
            allowMethod(Get)
            allowMethod(Post)
            allowMethod(Delete)
            allowMethod(Options)
            allowMethod(Put)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowHeader("X-Requested-With")
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