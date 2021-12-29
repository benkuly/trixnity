package net.folivo.trixnity.client.api

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.api.model.media.FileTransferProgress
import net.folivo.trixnity.client.api.model.media.GetConfigResponse
import net.folivo.trixnity.client.api.model.media.ThumbnailResizingMethod
import net.folivo.trixnity.client.api.model.media.UploadResponse

class MediaApiClient(private val httpClient: MatrixHttpClient) {

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixmediav3config">matrix spec</a>
     */
    suspend fun getConfig(): Result<GetConfigResponse> =
        httpClient.request {
            method = HttpMethod.Get
            url("/_matrix/media/v3/config")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixmediav3upload">matrix spec</a>
     */
    suspend fun upload(
        content: ByteReadChannel,
        contentLength: Long,
        contentType: ContentType,
        filename: String? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
        timeout: Long = 600_000
    ): Result<UploadResponse> =
        httpClient.request {
            method = HttpMethod.Post
            url("/_matrix/media/v3/upload")
            accept(ContentType.Application.Json)
            parameter("filename", filename)
            body = object : OutgoingContent.ReadChannelContent() {
                override val contentType: ContentType
                    get() = contentType
                override val contentLength: Long
                    get() = contentLength

                override fun readFrom(): ByteReadChannel {
                    return content
                }
            }
            timeout {
                requestTimeoutMillis = timeout
            }
            if (progress != null)
                onUpload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixmediav3downloadservernamemediaid">matrix spec</a>
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
        val response = httpClient.request<HttpResponse> {
            method = HttpMethod.Get
            url("/_matrix/media/v3/download/${downloadUri}")
            parameter("allow_remote", allowRemote)
            timeout {
                requestTimeoutMillis = timeout
            }
            if (progress != null)
                onDownload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }
        return response.mapCatching {
            DownloadResponse(
                content = it.receive(),
                contentLength = it.contentLength(),
                contentType = it.contentType(),
                filename = it.headers[HttpHeaders.ContentDisposition]
            )
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixmediav3thumbnailservernamemediaid">matrix spec</a>
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
        val response = httpClient.request<HttpResponse> {
            this.method = HttpMethod.Get
            url("/_matrix/media/v3/thumbnail/${downloadUri}")
            parameter("width", width)
            parameter("height", height)
            parameter("method", method.value)
            parameter("allow_remote", allowRemote)
            timeout {
                requestTimeoutMillis = 300000
            }
            if (progress != null)
                onDownload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }
        return response.mapCatching {
            DownloadResponse(
                content = it.receive(),
                contentLength = it.contentLength(),
                contentType = it.contentType(),
                filename = it.headers[HttpHeaders.ContentDisposition]
            )
        }
    }
}