package net.folivo.trixnity.applicationserviceapi.server

import io.ktor.server.application.*
import io.ktor.server.auth.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixApiServer
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

fun Application.matrixApplicationServiceApiServer(
    hsToken: String,
    eventContentSerializerMappings: EventContentSerializerMappings = DefaultEventContentSerializerMappings,
    json: Json = createMatrixEventJson(eventContentSerializerMappings),
    block: Application.() -> Unit,
) {
    matrixApiServer(json) {
        install(Authentication) {
            matrixQueryParameter(null, "access_token", hsToken)
        }
        block()
    }
}