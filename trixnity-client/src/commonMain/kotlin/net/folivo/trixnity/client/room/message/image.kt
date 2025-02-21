package net.folivo.trixnity.client.room.message

import io.ktor.http.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.ImageInfo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.ThumbnailInfo
import net.folivo.trixnity.utils.ByteArrayFlow

suspend fun MessageBuilder.image(
    body: String,
    image: ByteArrayFlow,
    format: String? = null,
    formattedBody: String? = null,
    fileName: String? = null,
    type: ContentType? = null,
    size: Long? = null,
    height: Int? = null,
    width: Int? = null,
    thumbnail: ByteArrayFlow? = null,
    thumbnailInfo: ThumbnailInfo? = null,
) {
    val info: ImageInfo?
    val url: String?
    val encryptedFile: EncryptedFile?
    val isEncryptedRoom = roomService.getById(roomId).first()?.encrypted == true
    if (isEncryptedRoom) {
        encryptedFile = mediaService.prepareUploadEncryptedMedia(image)
        val encryptedThumbnailFile = thumbnail?.let { mediaService.prepareUploadEncryptedMedia(it) }
        info = ImageInfo(
            height = height,
            width = width,
            mimeType = type?.toString(),
            size = size,
            thumbnailUrl = null,
            thumbnailFile = encryptedThumbnailFile,
            thumbnailInfo = thumbnailInfo
        )
        url = null
    } else {
        url = mediaService.prepareUploadMedia(image, type)
        val thumbnailUrl = thumbnail?.let {
            val thumbnailType = thumbnailInfo?.mimeType?.let { mimeType ->
                try {
                    ContentType.parse(mimeType)
                } catch (_: Exception) {
                    null
                }
            }
            mediaService.prepareUploadMedia(thumbnail, thumbnailType)
        }
        info = ImageInfo(
            height = height,
            width = width,
            mimeType = type?.toString(),
            size = size,
            thumbnailUrl = thumbnailUrl,
            thumbnailFile = null,
            thumbnailInfo = thumbnailInfo
        )
        encryptedFile = null
    }
    roomMessageBuilder(body, format, formattedBody) {
        RoomMessageEventContent.FileBased.Image(
            body = this.body,
            format = this.format,
            formattedBody = this.formattedBody,
            fileName = fileName,
            info = info,
            url = url,
            file = encryptedFile,
            relatesTo = relatesTo,
            mentions = mentions,
        )
    }
}