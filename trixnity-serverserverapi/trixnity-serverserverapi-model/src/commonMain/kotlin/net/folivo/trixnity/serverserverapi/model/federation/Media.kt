package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.http.*
import io.ktor.utils.io.*

sealed interface Media {
    data class Stream(
        val content: ByteReadChannel,
        val contentLength: Long?,
        val contentType: ContentType?,
        val contentDisposition: ContentDisposition?,
    ) : Media

    data class Redirect(
        val location: String,
    ) : Media
}
