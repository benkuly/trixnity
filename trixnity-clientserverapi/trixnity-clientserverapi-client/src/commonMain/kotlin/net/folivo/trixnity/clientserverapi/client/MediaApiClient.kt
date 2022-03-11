package net.folivo.trixnity.clientserverapi.client

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.resources.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.clientserverapi.model.media.*

class MediaApiClient(private val httpClient: MatrixClientServerApiHttpClient) {

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3config">matrix spec</a>
     */
    suspend fun getConfig(): Result<GetMediaConfig.Response> =
        httpClient.request(GetMediaConfig)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixmediav3upload">matrix spec</a>
     */
    suspend fun upload(
        content: ByteReadChannel,
        contentLength: Long,
        contentType: ContentType,
        filename: String? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Long = 600_000
    ): Result<UploadMedia.Response> =
        httpClient.request(UploadMedia(filename, contentType), object : OutgoingContent.ReadChannelContent() {
            override val contentType = contentType
            override val contentLength = contentLength
            override fun readFrom() = content
        }) {
            timeout {
                requestTimeoutMillis = timeout
            }
            if (progress != null)
                onUpload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3downloadservernamemediaid">matrix spec</a>
     */
    suspend fun download(
        mxcUri: String,
        allowRemote: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Long = 600_000
    ): Result<DownloadResponse> {
        val uri = Url(mxcUri)
        if (uri.protocol.name != "mxc") return Result.failure(IllegalArgumentException("url protocol was not mxc"))
        val downloadUri = mxcUri.removePrefix("mxc://")
        val response = kotlin.runCatching {
            httpClient.baseClient.request(DownloadMedia(downloadUri, allowRemote)) {
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
        return response.mapCatching {
            DownloadResponse(
                content = it.body(),
                contentLength = it.contentLength(),
                contentType = it.contentType(),
                filename = it.headers[HttpHeaders.ContentDisposition]
            )
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3thumbnailservernamemediaid">matrix spec</a>
     */
    suspend fun downloadThumbnail(
        mxcUri: String,
        width: UInt,
        height: UInt,
        method: ThumbnailResizingMethod,
        allowRemote: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
    ): Result<DownloadResponse> {
        val uri = Url(mxcUri)
        if (uri.protocol.name != "mxc") return Result.failure(IllegalArgumentException("url protocol was not mxc"))
        val downloadUri = mxcUri.removePrefix("mxc://")
        val response = kotlin.runCatching {
            httpClient.baseClient.request(
                DownloadThumbnail(
                    downloadUri = downloadUri,
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
        return response.mapCatching {
            DownloadResponse(
                content = it.body(),
                contentLength = it.contentLength(),
                contentType = it.contentType(),
                filename = it.headers[HttpHeaders.ContentDisposition]
            )
        }
    }
}