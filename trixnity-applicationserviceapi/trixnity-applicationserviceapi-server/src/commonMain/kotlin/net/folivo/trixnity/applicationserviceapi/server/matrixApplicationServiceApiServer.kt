package net.folivo.trixnity.applicationserviceapi.server

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.default

fun Application.matrixApplicationServiceApiServer(
    hsToken: String,
    eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.default,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
    routes: Route.() -> Unit,
) {
    install(Authentication) {
        matrixQueryParameterOrBearer("matrix-query-parameter-auth", "access_token", hsToken)
    }
    matrixApiServer(json) {
        authenticate("matrix-query-parameter-auth") {
            routes()
        }
    }
}