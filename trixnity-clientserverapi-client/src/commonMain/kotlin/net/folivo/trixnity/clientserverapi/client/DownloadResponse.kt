package net.folivo.trixnity.clientserverapi.client

import io.ktor.http.*
import io.ktor.utils.io.*

data class DownloadResponse(
    val content: ByteReadChannel,
    val contentLength: Long?,
    val contentType: ContentType?,
    val filename: String?,
)