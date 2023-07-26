package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

fun Application.matrixClientServerApiServer(
    accessTokenAuthenticationFunction: AccessTokenAuthenticationFunction,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
    routes: Route.() -> Unit,
) {
    installMatrixAccessTokenAuth("matrix-access-token-auth") {
        this.authenticationFunction = accessTokenAuthenticationFunction
    }
    matrixApiServer(json) {
        createChild(object : RouteSelector() {
            override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
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
    install(ConvertMediaPlugin)
    // see also https://spec.matrix.org/v1.7/client-server-api/#web-browser-clients
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