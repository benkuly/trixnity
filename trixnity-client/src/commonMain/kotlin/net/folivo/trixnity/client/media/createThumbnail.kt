package net.folivo.trixnity.client.media

import io.ktor.http.*

class Thumbnail(
    val file: ByteArray,
    val contentType: ContentType,
    val width: Int? = null,
    val height: Int? = null
)

expect suspend fun createThumbnail(file: ByteArray, contentType: ContentType, maxWidth: Int, maxHeight: Int): Thumbnail