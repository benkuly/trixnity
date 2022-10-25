package net.folivo.trixnity.client.room.message

import io.ktor.http.*
import net.folivo.trixnity.core.ByteFlow
import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.ImageInfo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.ImageMessageEventContent

@TrixnityDsl
suspend fun MessageBuilder.image(
    body: String,
    image: ByteFlow,
    type: ContentType,
    size: Int? = null,
    height: Int? = null,
    width: Int? = null
) {
    val format: ImageInfo?
    val url: String?
    val encryptedFile: EncryptedFile?
    if (isEncryptedRoom) {
        val (thumbnailFile, thumbnailInfo) = mediaService.prepareUploadEncryptedThumbnail(image, type)
            ?: Pair(null, null)

        encryptedFile = mediaService.prepareUploadEncryptedMedia(image)
        format = ImageInfo(
            height = height,
            width = width,
            mimeType = type.toString(),
            size = size,
            thumbnailUrl = null,
            thumbnailFile = thumbnailFile,
            thumbnailInfo = thumbnailInfo
        )
        url = null
    } else {
        url = mediaService.prepareUploadMedia(image, type)
        val (thumbnailUrl, thumbnailInfo) = mediaService.prepareUploadThumbnail(image, type) ?: Pair(null, null)
        format = ImageInfo(
            height = height,
            width = width,
            mimeType = type.toString(),
            size = size,
            thumbnailUrl = thumbnailUrl,
            thumbnailFile = null,
            thumbnailInfo = thumbnailInfo
        )
        encryptedFile = null
    }
    content = ImageMessageEventContent(body, format, url, encryptedFile)
}