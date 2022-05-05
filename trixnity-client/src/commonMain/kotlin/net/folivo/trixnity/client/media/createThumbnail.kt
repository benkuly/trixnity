package net.folivo.trixnity.client.media

import com.soywiz.korim.bitmap.resizedUpTo
import com.soywiz.korim.format.PNG
import com.soywiz.korim.format.encode
import com.soywiz.korim.format.readBitmap
import com.soywiz.korio.file.std.asMemoryVfsFile
import io.ktor.http.*

class Thumbnail(
    val file: ByteArray,
    val contentType: ContentType,
    val width: Int? = null,
    val height: Int? = null
)

suspend fun createThumbnail(file: ByteArray, maxWidth: Int, maxHeight: Int): Thumbnail {
    val image = file.asMemoryVfsFile().readBitmap()
    val resizedImage = image.resizedUpTo(maxWidth, maxHeight)
    return Thumbnail(
        file = resizedImage.encode(PNG),
        contentType = ContentType.Image.PNG,
        width = resizedImage.width,
        height = resizedImage.height
    )
}