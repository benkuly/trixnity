package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.media.*

interface MediaApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3config">matrix spec</a>
     */
    suspend fun getMediaConfig(context: MatrixEndpointContext<GetMediaConfig, Unit, GetMediaConfig.Response>): GetMediaConfig.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixmediav3upload">matrix spec</a>
     */
    suspend fun uploadMedia(context: MatrixEndpointContext<UploadMedia, Media, UploadMedia.Response>): UploadMedia.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3downloadservernamemediaid">matrix spec</a>
     */
    suspend fun downloadMedia(context: MatrixEndpointContext<DownloadMedia, Unit, Media>): Media

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3thumbnailservernamemediaid">matrix spec</a>
     */
    suspend fun downloadThumbnail(context: MatrixEndpointContext<DownloadThumbnail, Unit, Media>): Media

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3preview_url">matrix spec</a>
     */
    suspend fun getUrlPreview(context: MatrixEndpointContext<GetUrlPreview, Unit, GetUrlPreview.Response>): GetUrlPreview.Response
}