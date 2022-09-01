package net.folivo.trixnity.clientserverapi.server

import io.ktor.http.*
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

fun Application.matrixClientServerApiServer(
    accessTokenAuthenticationFunction: AccessTokenAuthenticationFunction,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
    block: Application.() -> Unit,
) {
    matrixApiServer(json) {
        // TODO rate limit
        install(ConvertMediaPlugin)
        installMatrixAccessTokenAuth {
            this.authenticationFunction = accessTokenAuthenticationFunction
        }
        // see also https://spec.matrix.org/v1.3/client-server-api/#web-browser-clients
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
        block()
    }
}