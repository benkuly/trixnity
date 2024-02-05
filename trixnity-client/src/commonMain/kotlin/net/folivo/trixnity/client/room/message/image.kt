package net.folivo.trixnity.client.room.message

import io.ktor.http.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.ImageInfo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.TrixnityDsl

@TrixnityDsl
suspend fun MessageBuilder.image(
    body: String,
    image: ByteArrayFlow,
    type: ContentType? = null,
    size: Int? = null,
    height: Int? = null,
    width: Int? = null
) {
    val format: ImageInfo?
    val url: String?
    val encryptedFile: EncryptedFile?
    val isEncryptedRoom = roomService.getById(roomId).first()?.encrypted == true
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
    contentBuilder = { relatesTo, mentions, newContentMentions ->
        when (relatesTo) {
            is RelatesTo.Replace -> RoomMessageEventContent.FileBased.Image(
                body = "* $body",
                info = format,
                url = url,
                file = encryptedFile,
                relatesTo = relatesTo.copy(
                    newContent = RoomMessageEventContent.FileBased.Image(
                        body = body,
                        info = format,
                        url = url,
                        file = encryptedFile,
                        mentions = newContentMentions,
                    )
                ),
                mentions = mentions,
            )

            else -> RoomMessageEventContent.FileBased.Image(
                body = body,
                info = format,
                url = url,
                file = encryptedFile,
                relatesTo = relatesTo,
                mentions = mentions,
            )
        }
    }
}