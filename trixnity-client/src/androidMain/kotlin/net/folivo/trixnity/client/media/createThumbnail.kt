package net.folivo.trixnity.client.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream


actual suspend fun createThumbnail(
    file: ByteArray,
    contentType: ContentType,
    maxWidth: Int,
    maxHeight: Int
): Thumbnail =
    withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        val thumbnail = kotlin.runCatching {
            ThumbnailUtils.extractThumbnail(BitmapFactory.decodeByteArray(file, 0, file.size), maxWidth, maxHeight).also {
                it.compress(Bitmap.CompressFormat.PNG, 80, output)
            }
        }
        thumbnail.exceptionOrNull()?.let { throw ThumbnailCreationException(it) }
        val width = thumbnail.getOrNull()?.width
        val height = thumbnail.getOrNull()?.height
        thumbnail.getOrNull()?.recycle()
        Thumbnail(output.toByteArray(), ContentType.Image.PNG, width, height)
    }