package net.folivo.trixnity.client.media

import io.ktor.http.*
import korlibs.image.bitmap.resizedUpTo
import korlibs.image.format.PNG
import korlibs.image.format.encode
import korlibs.image.format.readBitmap
import korlibs.io.file.std.asMemoryVfsFile

class Thumbnail(
    val file: ByteArray,
    val contentType: ContentType,
    val width: Int? = null,
    val height: Int? = null
)

suspend fun createThumbnail(file: ByteArray, maxWidth: Int, maxHeight: Int): Thumbnail {
    val image = file.asMemoryVfsFile().readBitmap()
    if (image.width == 0 || image.height == 0) throw IllegalArgumentException("cannot create thumbnail from image with at least one dimension of 0")
    val resizedImage = image.resizedUpTo(maxWidth, maxHeight)
    if (resizedImage.width == 0 || resizedImage.height == 0) throw IllegalStateException("generated thumbnail has at least one dimension of 0")
    return Thumbnail(
        file = resizedImage.encode(PNG),
        contentType = ContentType.Image.PNG,
        width = resizedImage.width,
        height = resizedImage.height
    )
}