package net.folivo.trixnity.client.api.media

import io.ktor.http.*
import io.ktor.utils.io.*

data class DownloadResponse(
    val content: ByteReadChannel,
    val contentLength: Long?,
    val contentType: ContentType?,
    val filename: String?,
)