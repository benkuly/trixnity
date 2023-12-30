package net.folivo.trixnity.client.room.message

import io.ktor.http.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VideoMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.VideoInfo
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.TrixnityDsl

@TrixnityDsl
suspend fun MessageBuilder.video(
    body: String,
    video: ByteArrayFlow,
    type: ContentType? = null,
    size: Int? = null,
    height: Int? = null,
    width: Int? = null,
    duration: Int? = null
) {
    val format: VideoInfo?
    val url: String?
    val encryptedFile: EncryptedFile?
    val isEncryptedRoom = roomService.getById(roomId).first()?.encrypted == true
    if (isEncryptedRoom) {
        val (thumbnailFile, thumbnailInfo) = mediaService.prepareUploadEncryptedThumbnail(video, type)
            ?: Pair(null, null)

        encryptedFile = mediaService.prepareUploadEncryptedMedia(video)
        format = VideoInfo(
            duration = duration,
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
        url = mediaService.prepareUploadMedia(video, type)
        val (thumbnailUrl, thumbnailInfo) = mediaService.prepareUploadThumbnail(video, type) ?: Pair(null, null)
        format = VideoInfo(
            duration = duration,
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
            is RelatesTo.Replace -> VideoMessageEventContent(
                body = "* $body",
                info = format,
                url = url,
                file = encryptedFile,
                relatesTo = relatesTo.copy(
                    newContent = VideoMessageEventContent(
                        body = body,
                        info = format,
                        url = url,
                        file = encryptedFile,
                        mentions = newContentMentions,
                    )
                ),
                mentions = mentions,
            )

            else -> VideoMessageEventContent(
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