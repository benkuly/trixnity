package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.clientserverapi.model.media.*
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.mediaApiRoutes(
    handler: MediaApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings,
) {
    authenticate {
        matrixEndpoint<GetMediaConfig, GetMediaConfig.Response>(json, contentMappings) {
            handler.getMediaConfig(this)
        }
        matrixEndpoint<UploadMedia, Media, UploadMedia.Response>(json, contentMappings) {
            handler.uploadMedia(this)
        }
        matrixEndpoint<DownloadMedia, Media>(json, contentMappings) {
            handler.downloadMedia(this)
        }
        matrixEndpoint<DownloadThumbnail, Media>(json, contentMappings) {
            handler.downloadThumbnail(this)
        }
    }
}