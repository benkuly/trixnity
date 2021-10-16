package net.folivo.trixnity.client.media

import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.coobird.thumbnailator.Thumbnails
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

actual suspend fun createThumbnail(
    file: ByteArray,
    contentType: ContentType,
    maxWidth: Int,
    maxHeight: Int
): Thumbnail =
    withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        val thumbnail = kotlin.runCatching {
            Thumbnails.of(ByteArrayInputStream(file))
                .size(maxWidth, maxHeight)
                .asBufferedImage()
                .also {
                    ImageIO.write(it, "jpg", output)
                }
        }
        thumbnail.exceptionOrNull()?.let { throw ThumbnailCreationException(it) }
        val width = thumbnail.getOrNull()?.width
        val height = thumbnail.getOrNull()?.height
        Thumbnail(output.toByteArray(), ContentType.Image.JPEG, width, height)
    }