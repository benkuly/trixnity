package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.media.*

interface MediaApiHandler {
    /**
     * @see [GetMediaConfig]
     */
    suspend fun getMediaConfig(context: MatrixEndpointContext<GetMediaConfig, Unit, GetMediaConfig.Response>): GetMediaConfig.Response

    /**
     * @see [CreateMedia]
     */
    suspend fun createMedia(context: MatrixEndpointContext<CreateMedia, Unit, CreateMedia.Response>): CreateMedia.Response

    /**
     * @see [UploadMedia]
     */
    suspend fun uploadMedia(context: MatrixEndpointContext<UploadMedia, Media, UploadMedia.Response>): UploadMedia.Response

    /**
     * @see [UploadMediaByContentUri]
     */
    suspend fun uploadMediaByContentUri(context: MatrixEndpointContext<UploadMediaByContentUri, Media, Unit>)

    /**
     * @see [DownloadMedia]
     */
    suspend fun downloadMedia(context: MatrixEndpointContext<DownloadMedia, Unit, Media>): Media

    /**
     * @see [DownloadThumbnail]
     */
    suspend fun downloadThumbnail(context: MatrixEndpointContext<DownloadThumbnail, Unit, Media>): Media

    /**
     * @see [GetUrlPreview]
     */
    suspend fun getUrlPreview(context: MatrixEndpointContext<GetUrlPreview, Unit, GetUrlPreview.Response>): GetUrlPreview.Response
}