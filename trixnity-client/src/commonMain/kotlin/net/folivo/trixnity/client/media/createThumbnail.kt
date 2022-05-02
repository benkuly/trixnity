package net.folivo.trixnity.client.media

import com.soywiz.korim.bitmap.resizedUpTo
import com.soywiz.korim.format.PNG
import com.soywiz.korim.format.encode
import com.soywiz.korim.format.readNativeImage
import com.soywiz.korio.file.std.VfsFileFromData
import io.ktor.http.*

class Thumbnail(
    val file: ByteArray,
    val contentType: ContentType,
    val width: Int? = null,
    val height: Int? = null
)

suspend fun createThumbnail(file: ByteArray, maxWidth: Int, maxHeight: Int): Thumbnail {
    val image = VfsFileFromData(file).readNativeImage()
    val resizedImage = image.resizedUpTo(maxWidth, maxHeight)
    return Thumbnail(resizedImage.encode(PNG), ContentType.Image.PNG, resizedImage.width, resizedImage.height)
}