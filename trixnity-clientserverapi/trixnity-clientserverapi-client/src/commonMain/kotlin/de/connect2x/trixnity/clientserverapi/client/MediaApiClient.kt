package de.connect2x.trixnity.clientserverapi.client

import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import de.connect2x.trixnity.clientserverapi.model.media.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface MediaApiClient {
    /**
     * @see [GetMediaConfigLegacy]
     */
    @Deprecated("use getConfig instead")
    suspend fun getConfigLegacy(): Result<GetMediaConfigLegacy.Response>

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
     * @see [DownloadMediaLegacy]
     */
    @Deprecated("use download instead")
    suspend fun <T> downloadLegacy(
        mxcUri: String,
        allowRemote: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Duration = Duration.INFINITE,
        downloadHandler: suspend (Media) -> T
    ): Result<T>

    /**
     * @see [DownloadMedia]
     */
    suspend fun <T> download(
        mxcUri: String,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Duration? = null,
        downloadHandler: suspend (Media) -> T
    ): Result<T>

    /**
     * @see [DownloadThumbnailLegacy]
     */
    @Deprecated("use downloadThumbnail instead")
    suspend fun <T> downloadThumbnailLegacy(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        allowRemote: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Duration = Duration.INFINITE,
        downloadHandler: suspend (Media) -> T
    ): Result<T>

    /**
     * @see [DownloadThumbnail]
     */
    suspend fun <T> downloadThumbnail(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        animated: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Duration? = null,
        downloadHandler: suspend (Media) -> T
    ): Result<T>

    /**
     * @see [GetUrlPreviewLegacy]
     */
    @Deprecated("use getUrlPreview instead")
    suspend fun getUrlPreviewLegacy(
        url: String,
        timestamp: Long? = null,
    ): Result<GetUrlPreviewLegacy.Response>

    /**
     * @see [GetUrlPreview]
     */
    suspend fun getUrlPreview(
        url: String,
        timestamp: Long? = null,
    ): Result<GetUrlPreview.Response>
}

class MediaApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient
) : MediaApiClient {

    @Deprecated("use getConfig instead")
    @Suppress("DEPRECATION")
    override suspend fun getConfigLegacy(): Result<GetMediaConfigLegacy.Response> =
        baseClient.request(GetMediaConfigLegacy)

    override suspend fun getConfig(): Result<GetMediaConfig.Response> =
        baseClient.request(GetMediaConfig)

    override suspend fun createMedia(): Result<CreateMedia.Response> =
        baseClient.request(CreateMedia)

    override suspend fun upload(
        media: Media,
        progress: MutableStateFlow<FileTransferProgress?>?,
        timeout: Duration,
    ): Result<UploadMedia.Response> =
        baseClient.request(UploadMedia(media.contentDisposition?.parameter("filename")), media) {
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
        baseClient.request(
            UploadMediaByContentUri(
                serverName,
                mediaId,
                media.contentDisposition?.parameter("filename")
            ), media
        ) {
            timeout {
                requestTimeoutMillis = timeout.inWholeMilliseconds
            }
            if (progress != null)
                onUpload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }

    @Deprecated("use download instead")
    @Suppress("DEPRECATION")
    override suspend fun <T> downloadLegacy(
        mxcUri: String,
        allowRemote: Boolean?,
        progress: MutableStateFlow<FileTransferProgress?>?,
        timeout: Duration,
        downloadHandler: suspend (Media) -> T
    ): Result<T> {
        val uri = Url(mxcUri)
        if (uri.protocol.name != "mxc") return Result.failure(IllegalArgumentException("url protocol was not mxc"))
        val (serverName, mediaId) = mxcUri.removePrefix("mxc://")
            .let { it.substringBefore("/") to it.substringAfter("/") }
        return baseClient.withRequest(
            endpoint = DownloadMediaLegacy(serverName, mediaId, allowRemote, timeoutMs = timeout.inWholeMilliseconds),
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

    override suspend fun <T> download(
        mxcUri: String,
        progress: MutableStateFlow<FileTransferProgress?>?,
        timeout: Duration?,
        downloadHandler: suspend (Media) -> T
    ): Result<T> {
        val uri = Url(mxcUri)
        if (uri.protocol.name != "mxc") return Result.failure(IllegalArgumentException("url protocol was not mxc"))
        val (serverName, mediaId) = mxcUri.removePrefix("mxc://")
            .let { it.substringBefore("/") to it.substringAfter("/") }
        return baseClient.withRequest(
            endpoint = DownloadMedia(serverName, mediaId, timeout?.inWholeMilliseconds),
            requestBuilder = {
                method = HttpMethod.Get
                if (timeout != null)
                    timeout {
                        requestTimeoutMillis =
                            timeout.plus(10.seconds).inWholeMilliseconds
                    }
                if (progress != null)
                    onDownload { transferred, total ->
                        progress.value = FileTransferProgress(transferred, total)
                    }
            },
            responseHandler = downloadHandler
        )
    }

    @Deprecated("use downloadThumbnail instead")
    @Suppress("DEPRECATION")
    override suspend fun <T> downloadThumbnailLegacy(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        allowRemote: Boolean?,
        progress: MutableStateFlow<FileTransferProgress?>?,
        timeout: Duration,
        downloadHandler: suspend (Media) -> T
    ): Result<T> {
        val uri = Url(mxcUri)
        if (uri.protocol.name != "mxc") return Result.failure(IllegalArgumentException("url protocol was not mxc"))
        val (serverName, mediaId) = mxcUri.removePrefix("mxc://")
            .let { it.substringBefore("/") to it.substringAfter("/") }
        return baseClient.withRequest(
            endpoint = DownloadThumbnailLegacy(
                serverName = serverName,
                mediaId = mediaId,
                width = width,
                height = height,
                method = method,
                allowRemote = allowRemote,
                timeoutMs = timeout.inWholeMilliseconds,
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

    override suspend fun <T> downloadThumbnail(
        mxcUri: String,
        width: Long,
        height: Long,
        method: ThumbnailResizingMethod,
        animated: Boolean?,
        progress: MutableStateFlow<FileTransferProgress?>?,
        timeout: Duration?,
        downloadHandler: suspend (Media) -> T
    ): Result<T> {
        val uri = Url(mxcUri)
        if (uri.protocol.name != "mxc") return Result.failure(IllegalArgumentException("url protocol was not mxc"))
        val (serverName, mediaId) = mxcUri.removePrefix("mxc://")
            .let { it.substringBefore("/") to it.substringAfter("/") }
        return baseClient.withRequest(
            endpoint = DownloadThumbnail(
                serverName = serverName,
                mediaId = mediaId,
                width = width,
                height = height,
                method = method,
                animated = animated,
                timeoutMs = timeout?.inWholeMilliseconds
            ),
            requestBuilder = {
                this.method = HttpMethod.Get
                if (timeout != null)
                    timeout {
                        requestTimeoutMillis = timeout.plus(10.seconds).inWholeMilliseconds
                    }
                if (progress != null)
                    onDownload { transferred, total ->
                        progress.value = FileTransferProgress(transferred, total)
                    }
            },
            responseHandler = downloadHandler
        )
    }

    @Deprecated("use getUrlPreview instead")
    @Suppress("DEPRECATION")
    override suspend fun getUrlPreviewLegacy(
        url: String,
        timestamp: Long?,
    ): Result<GetUrlPreviewLegacy.Response> =
        baseClient.request(GetUrlPreviewLegacy(url, timestamp))

    override suspend fun getUrlPreview(
        url: String,
        timestamp: Long?,
    ): Result<GetUrlPreview.Response> =
        baseClient.request(GetUrlPreview(url, timestamp))
}