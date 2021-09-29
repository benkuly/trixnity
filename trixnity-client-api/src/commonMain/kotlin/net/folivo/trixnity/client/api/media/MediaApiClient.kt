package net.folivo.trixnity.client.api.media

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.MutableStateFlow

class MediaApiClient(private val httpClient: HttpClient) {

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-media-r0-config">matrix spec</a>
     */
    suspend fun getConfig(): GetConfigResponse {
        return httpClient.get("/_matrix/media/r0/config") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
    }

    private class StreamContent(
        private val content: ByteReadChannel,
        override val contentLength: Long
    ) : OutgoingContent.ReadChannelContent() {
        override fun readFrom(): ByteReadChannel {
            return content
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-media-r0-upload">matrix spec</a>
     */
    suspend fun upload(
        content: ByteReadChannel,
        contentLength: Long,
        contentType: ContentType,
        filename: String? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null
    ): UploadResponse {
        return httpClient.post("/_matrix/media/r0/upload") {
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
                requestTimeoutMillis = 600000
            }
            if (progress != null)
                onUpload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-media-r0-download-servername-mediaid">matrix spec</a>
     */
    suspend fun download(
        mxcUri: String,
        allowRemote: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
    ): DownloadResponse {
        val uri = Url(mxcUri)
        require(uri.protocol.name == "mxc") { "uri protocol was not mxc" }
        val response = httpClient.get<HttpResponse> {
            url("/_matrix/media/r0/download/${uri.host}${uri.encodedPath}")
            parameter("allow_remote", allowRemote)
            timeout {
                requestTimeoutMillis = 600000
            }
            if (progress != null)
                onDownload { transferred, total ->
                    progress.value = FileTransferProgress(transferred, total)
                }
        }
        return DownloadResponse(
            content = response.receive(),
            contentLength = response.contentLength(),
            contentType = response.contentType(),
            filename = response.headers[HttpHeaders.ContentDisposition]
        )
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-media-r0-thumbnail-servername-mediaid">matrix spec</a>
     */
    suspend fun downloadThumbnail(
        mxcUri: String,
        width: UInt,
        height: UInt,
        method: ThumbnailResizingMethod,
        allowRemote: Boolean? = null,
        progress: MutableStateFlow<FileTransferProgress?>? = null,
    ): DownloadResponse {
        val uri = Url(mxcUri)
        require(uri.protocol.name == "mxc") { "uri protocol was not mxc" }
        val response = httpClient.get<HttpResponse> {
            url("/_matrix/media/r0/thumbnail/${uri.host}${uri.encodedPath}")
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
        return DownloadResponse(
            content = response.receive(),
            contentLength = response.contentLength(),
            contentType = response.contentType(),
            filename = response.headers[HttpHeaders.ContentDisposition]
        )
    }
}