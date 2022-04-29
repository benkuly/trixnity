package net.folivo.trixnity.client.media

import io.ktor.http.*

actual suspend fun createThumbnail(
    file: ByteArray,
    contentType: ContentType,
    maxWidth: Int,
    maxHeight: Int
): Thumbnail {
    TODO("Not yet implemented")
}