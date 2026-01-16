package de.connect2x.trixnity.clientserverapi.server

import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.api.server.matrixEndpoint
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.mediaApiRoutes(
    handler: MediaApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    @Suppress("DEPRECATION")
    matrixEndpoint(json, contentMappings, handler::getMediaConfigLegacy)
    matrixEndpoint(json, contentMappings, handler::getMediaConfig)
    matrixEndpoint(json, contentMappings, handler::createMedia)
    matrixEndpoint(json, contentMappings, handler::uploadMedia)
    matrixEndpoint(json, contentMappings, handler::uploadMediaByContentUri)
    @Suppress("DEPRECATION")
    matrixEndpoint(json, contentMappings, handler::downloadMediaLegacy)
    matrixEndpoint(json, contentMappings, handler::downloadMedia)
    matrixEndpoint(json, contentMappings, handler::downloadMediaWithFileName)
    @Suppress("DEPRECATION")
    matrixEndpoint(json, contentMappings, handler::downloadThumbnailLegacy)
    matrixEndpoint(json, contentMappings, handler::downloadThumbnail)
    @Suppress("DEPRECATION")
    matrixEndpoint(json, contentMappings, handler::getUrlPreviewLegacy)
    matrixEndpoint(json, contentMappings, handler::getUrlPreview)
}