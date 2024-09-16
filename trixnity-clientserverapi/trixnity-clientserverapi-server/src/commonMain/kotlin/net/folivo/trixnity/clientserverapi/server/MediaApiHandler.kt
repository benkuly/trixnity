package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.media.*

interface MediaApiHandler {
    /**
     * @see [GetMediaConfigLegacy]
     */
    @Deprecated("use getMediaConfig instead")
    suspend fun getMediaConfigLegacy(context: MatrixEndpointContext<GetMediaConfigLegacy, Unit, GetMediaConfigLegacy.Response>): GetMediaConfigLegacy.Response

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
     * @see [DownloadMediaLegacy]
     */
    @Deprecated("use downloadMedia instead")
    suspend fun downloadMediaLegacy(context: MatrixEndpointContext<DownloadMediaLegacy, Unit, Media>): Media

    /**
     * @see [DownloadMedia]
     */
    suspend fun downloadMedia(context: MatrixEndpointContext<DownloadMedia, Unit, Media>): Media

    /**
     * @see [DownloadThumbnailLegacy]
     */
    @Deprecated("use downloadThumbnail instead")
    suspend fun downloadThumbnailLegacy(context: MatrixEndpointContext<DownloadThumbnailLegacy, Unit, Media>): Media

    /**
     * @see [DownloadThumbnail]
     */
    suspend fun downloadThumbnail(context: MatrixEndpointContext<DownloadThumbnail, Unit, Media>): Media

    /**
     * @see [GetUrlPreviewLegacy]
     */
    @Deprecated("use getUrlPreview instead")
    suspend fun getUrlPreviewLegacy(context: MatrixEndpointContext<GetUrlPreviewLegacy, Unit, GetUrlPreviewLegacy.Response>): GetUrlPreviewLegacy.Response

    /**
     * @see [GetUrlPreviewLegacy]
     */
    suspend fun getUrlPreview(context: MatrixEndpointContext<GetUrlPreview, Unit, GetUrlPreview.Response>): GetUrlPreview.Response
}