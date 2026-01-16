package de.connect2x.trixnity.clientserverapi.model.media

import io.ktor.http.*
import io.ktor.utils.io.*

data class Media(
    val content: ByteReadChannel,
    val contentLength: Long?,
    val contentType: ContentType?,
    val contentDisposition: ContentDisposition?,
)