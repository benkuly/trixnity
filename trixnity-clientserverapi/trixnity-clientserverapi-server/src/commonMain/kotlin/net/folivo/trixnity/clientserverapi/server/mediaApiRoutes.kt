package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.mediaApiRoutes(
    handler: MediaApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    authenticate {
        matrixEndpoint(json, contentMappings, handler::getMediaConfig)
        matrixEndpoint(json, contentMappings, handler::uploadMedia)
        matrixEndpoint(json, contentMappings, handler::downloadMedia)
        matrixEndpoint(json, contentMappings, handler::downloadThumbnail)
        matrixEndpoint(json, contentMappings, handler::getUrlPreview)
    }
}