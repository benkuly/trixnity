package de.connect2x.trixnity.clientserverapi.server

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.api.server.matrixApiServer
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default

fun Application.matrixClientServerApiServer(
    accessTokenAuthenticationFunction: AccessTokenAuthenticationFunction,
    eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.default,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
    routes: Route.() -> Unit,
) {
    installMatrixAccessTokenAuth("matrix-access-token-auth") {
        this.authenticationFunction = accessTokenAuthenticationFunction
    }
    install(ConvertMediaPlugin)
    matrixApiServer(json) {
        createChild(object : RouteSelector() {
            override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
                RouteSelectorEvaluation.Transparent
        }).apply {
            installMatrixClientServerApiServer()
            authenticate("matrix-access-token-auth") {
                routes()
            }
        }
    }
}

fun Route.installMatrixClientServerApiServer() {
    // TODO rate limit
    // see also https://spec.matrix.org/v1.10/client-server-api/#web-browser-clients
    install(CORS) {
        anyHost()
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Delete)
        allowMethod(io.ktor.http.HttpMethod.Options)
        allowMethod(io.ktor.http.HttpMethod.Put)
        allowHeader(io.ktor.http.HttpHeaders.Authorization)
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
        allowHeader("X-Requested-With")
    }
    options("{...}") { }
}