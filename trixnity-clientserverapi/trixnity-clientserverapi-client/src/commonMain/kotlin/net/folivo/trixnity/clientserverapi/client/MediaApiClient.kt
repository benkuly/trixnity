package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.clientserverapi.model.media.*

interface IMediaApiClient {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3config">matrix spec</a>
     */
    suspend fun getConfig(): Result<GetMediaConfig.Response>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixmediav3upload">matrix spec</a>
     */
    suspend fun upload(
        media: Media,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Long = 600_000
    ): Result<UploadMedia.Response>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3downloadservernamemediaid">matrix spec</a>
     */
    suspend fun download(
        mxcUri: String,
        allowRemote: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Long = 600_000
    ): Result<Media>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3thumbnailservernamemediaid">matrix spec</a>
     */
    suspend fun downloadThumbnail(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        allowRemote: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
    ): Result<Media>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3preview_url">matrix spec</a>
     */
    suspend fun getUrlPreview(
        url: String,
        timestamp: Long? = null
    ): Result<GetUrlPreview.Response>
}

class MediaApiClient(private val httpClient: MatrixClientServerApiHttpClient) : IMediaApiClient {

    override suspend fun getConfig(): Result<GetMediaConfig.Response> =
        httpClient.request(GetMediaConfig)

    override suspend fun upload(
        media: Media,
        progress: MutableStateFlow<FileTransferProgress?>?,
        timeout: Long
    ): Result<UploadMedia.Response> =
        httpClient.request(UploadMedia(media.filename), media) {
            timeout {
                requestTimeoutMillis = timeout
            }
            if (progress != null)
                onUpload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }

    override suspend fun download(
        mxcUri: String,
        allowRemote: Boolean?,
        progress: MutableStateFlow<FileTransferProgress?>?,
        timeout: Long
    ): Result<Media> {
        val uri = Url(mxcUri)
        if (uri.protocol.name != "mxc") return Result.failure(IllegalArgumentException("url protocol was not mxc"))
        val (serverName, mediaId) = mxcUri.removePrefix("mxc://")
            .let { it.substringBefore("/") to it.substringAfter("/") }
        return httpClient.request(DownloadMedia(serverName, mediaId, allowRemote)) {
            method = HttpMethod.Get
            timeout {
                requestTimeoutMillis = timeout
            }
            if (progress != null)
                onDownload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }
    }

    override suspend fun downloadThumbnail(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        allowRemote: Boolean?,
        progress: MutableStateFlow<FileTransferProgress?>?,
    ): Result<Media> {
        val uri = Url(mxcUri)
        if (uri.protocol.name != "mxc") return Result.failure(IllegalArgumentException("url protocol was not mxc"))
        val (serverName, mediaId) = mxcUri.removePrefix("mxc://")
            .let { it.substringBefore("/") to it.substringAfter("/") }
        return httpClient.request(
            DownloadThumbnail(
                serverName = serverName,
                mediaId = mediaId,
                width = width,
                height = height,
                method = method,
                allowRemote = allowRemote,
            )
        ) {
            this.method = HttpMethod.Get
            timeout {
                requestTimeoutMillis = 300000
            }
            if (progress != null)
                onDownload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }
    }

    override suspend fun getUrlPreview(
        url: String,
        timestamp: Long?
    ): Result<GetUrlPreview.Response> =
        httpClient.request(GetUrlPreview(url.e(), timestamp))
}