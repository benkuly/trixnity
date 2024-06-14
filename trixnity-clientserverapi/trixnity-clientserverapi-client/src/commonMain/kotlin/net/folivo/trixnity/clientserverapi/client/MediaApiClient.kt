package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.clientserverapi.model.media.*
import kotlin.time.Duration

interface MediaApiClient {
    /**
     * @see [GetMediaConfig]
     */
    suspend fun getConfig(): Result<GetMediaConfig.Response>

    /**
     * @see [CreateMedia]
     */
    suspend fun createMedia(): Result<CreateMedia.Response>

    /**
     * @see [UploadMedia]
     */
    suspend fun upload(
        media: Media,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Duration = Duration.INFINITE,
    ): Result<UploadMedia.Response>

    /**
     * @see [UploadMediaByContentUri]
     */
    suspend fun upload(
        serverName: String,
        mediaId: String,
        media: Media,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Duration = Duration.INFINITE,
    ): Result<Unit>

    /**
     * @see [DownloadMedia]
     */
    @Deprecated("use download with downloadHandler")
    suspend fun download(
        mxcUri: String,
        allowRemote: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Duration = Duration.INFINITE,
    ): Result<Media>

    /**
     * @see [DownloadMedia]
     */
    suspend fun download(
        mxcUri: String,
        allowRemote: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Duration = Duration.INFINITE,
        downloadHandler: suspend (Media) -> Unit
    ): Result<Unit>

    /**
     * @see [DownloadThumbnail]
     */
    @Deprecated("use downloadThumbnail with downloadHandler")
    suspend fun downloadThumbnail(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        allowRemote: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Duration = Duration.INFINITE,
    ): Result<Media>

    /**
     * @see [DownloadThumbnail]
     */
    suspend fun downloadThumbnail(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        allowRemote: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Duration = Duration.INFINITE,
        downloadHandler: suspend (Media) -> Unit
    ): Result<Unit>

    /**
     * @see [GetUrlPreview]
     */
    suspend fun getUrlPreview(
        url: String,
        timestamp: Long? = null,
    ): Result<GetUrlPreview.Response>
}

class MediaApiClientImpl(private val httpClient: MatrixClientServerApiHttpClient) : MediaApiClient {

    override suspend fun getConfig(): Result<GetMediaConfig.Response> =
        httpClient.request(GetMediaConfig)

    override suspend fun createMedia(): Result<CreateMedia.Response> =
        httpClient.request(CreateMedia)

    override suspend fun upload(
        media: Media,
        progress: MutableStateFlow<FileTransferProgress?>?,
        timeout: Duration,
    ): Result<UploadMedia.Response> =
        httpClient.request(UploadMedia(media.filename), media) {
            timeout {
                requestTimeoutMillis = timeout.inWholeMilliseconds
            }
            if (progress != null)
                onUpload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }

    override suspend fun upload(
        serverName: String,
        mediaId: String,
        media: Media,
        progress: MutableStateFlow<FileTransferProgress?>?,
        timeout: Duration,
    ): Result<Unit> =
        httpClient.request(UploadMediaByContentUri(serverName, mediaId, media.filename), media) {
            timeout {
                requestTimeoutMillis = timeout.inWholeMilliseconds
            }
            if (progress != null)
                onUpload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }

    @Deprecated("use download with downloadHandler")
    override suspend fun download(
        mxcUri: String,
        allowRemote: Boolean?,
        progress: MutableStateFlow<FileTransferProgress?>?,
        timeout: Duration,
    ): Result<Media> {
        val uri = Url(mxcUri)
        if (uri.protocol.name != "mxc") return Result.failure(IllegalArgumentException("url protocol was not mxc"))
        val (serverName, mediaId) = mxcUri.removePrefix("mxc://")
            .let { it.substringBefore("/") to it.substringAfter("/") }
        return httpClient.request(DownloadMedia(serverName, mediaId, allowRemote)) {
            method = HttpMethod.Get
            timeout {
                requestTimeoutMillis = timeout.inWholeMilliseconds
            }
            if (progress != null)
                onDownload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }
    }

    override suspend fun download(
        mxcUri: String,
        allowRemote: Boolean?,
        progress: MutableStateFlow<FileTransferProgress?>?,
        timeout: Duration,
        downloadHandler: suspend (Media) -> Unit
    ): Result<Unit> {
        val uri = Url(mxcUri)
        if (uri.protocol.name != "mxc") return Result.failure(IllegalArgumentException("url protocol was not mxc"))
        val (serverName, mediaId) = mxcUri.removePrefix("mxc://")
            .let { it.substringBefore("/") to it.substringAfter("/") }
        return httpClient.withRequest(
            endpoint = DownloadMedia(serverName, mediaId, allowRemote),
            requestBuilder = {
                method = HttpMethod.Get
                timeout {
                    requestTimeoutMillis = timeout.inWholeMilliseconds
                }
                if (progress != null)
                    onDownload { transferred, total ->
                        progress.value = FileTransferProgress(transferred, total)
                    }
            },
            responseHandler = downloadHandler
        )
    }

    @Deprecated("use downloadThumbnail with downloadHandler")
    override suspend fun downloadThumbnail(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        allowRemote: Boolean?,
        progress: MutableStateFlow<FileTransferProgress?>?,
        timeout: Duration,
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
                requestTimeoutMillis = timeout.inWholeMilliseconds
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
        timeout: Duration,
        downloadHandler: suspend (Media) -> Unit
    ): Result<Unit> {
        val uri = Url(mxcUri)
        if (uri.protocol.name != "mxc") return Result.failure(IllegalArgumentException("url protocol was not mxc"))
        val (serverName, mediaId) = mxcUri.removePrefix("mxc://")
            .let { it.substringBefore("/") to it.substringAfter("/") }
        return httpClient.withRequest(
            endpoint = DownloadThumbnail(
                serverName = serverName,
                mediaId = mediaId,
                width = width,
                height = height,
                method = method,
                allowRemote = allowRemote,
            ),
            requestBuilder = {
                this.method = HttpMethod.Get
                timeout {
                    requestTimeoutMillis = timeout.inWholeMilliseconds
                }
                if (progress != null)
                    onDownload { transferred, total ->
                        progress.value = FileTransferProgress(transferred, total)
                    }
            },
            responseHandler = downloadHandler
        )
    }

    override suspend fun getUrlPreview(
        url: String,
        timestamp: Long?,
    ): Result<GetUrlPreview.Response> =
        httpClient.request(GetUrlPreview(url, timestamp))
}