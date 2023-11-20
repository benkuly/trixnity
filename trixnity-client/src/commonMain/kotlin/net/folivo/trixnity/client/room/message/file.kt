package net.folivo.trixnity.client.room.message

import io.ktor.http.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.FileInfo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.FileMessageEventContent
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.TrixnityDsl

@TrixnityDsl
suspend fun MessageBuilder.file(
    body: String,
    file: ByteArrayFlow,
    type: ContentType,
    size: Int? = null,
    name: String? = null
) {
    val format: FileInfo?
    val url: String?
    val encryptedFile: EncryptedFile?
    val isEncryptedRoom = roomService.getById(roomId).first()?.encrypted == true
    if (isEncryptedRoom) {
        val (thumbnailFile, thumbnailInfo) = mediaService.prepareUploadEncryptedThumbnail(file, type)
            ?: Pair(null, null)

        encryptedFile = mediaService.prepareUploadEncryptedMedia(file)
        format = FileInfo(
            mimeType = type.toString(),
            size = size,
            thumbnailUrl = null,
            thumbnailFile = thumbnailFile,
            thumbnailInfo = thumbnailInfo
        )
        url = null
    } else {
        url = mediaService.prepareUploadMedia(file, type)
        val (thumbnailUrl, thumbnailInfo) = mediaService.prepareUploadThumbnail(file, type) ?: Pair(null, null)
        format = FileInfo(
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
            is RelatesTo.Replace -> FileMessageEventContent(
                body = "* $body",
                fileName = name,
                info = format,
                url = url,
                file = encryptedFile,
                relatesTo = relatesTo.copy(
                    newContent = FileMessageEventContent(
                        body = body,
                        fileName = name,
                        info = format,
                        url = url,
                        file = encryptedFile,
                        mentions = newContentMentions,
                    )
                ),
                mentions = mentions,
            )

            else -> FileMessageEventContent(
                body = body,
                fileName = name,
                info = format,
                url = url,
                file = encryptedFile,
                relatesTo = relatesTo,
                mentions = mentions,
            )
        }
    }
}