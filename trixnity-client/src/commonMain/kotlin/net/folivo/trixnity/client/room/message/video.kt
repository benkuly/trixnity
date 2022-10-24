package net.folivo.trixnity.client.room.message

import io.ktor.http.*
import net.folivo.trixnity.core.ByteFlow
import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VideoMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.VideoInfo

@TrixnityDsl
suspend fun MessageBuilder.video(
    body: String,
    video: ByteFlow,
    type: ContentType,
    size: Int? = null,
    height: Int? = null,
    width: Int? = null,
    duration: Int? = null
) {
    val format: VideoInfo?
    val url: String?
    val encryptedFile: EncryptedFile?
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
    content = VideoMessageEventContent(body, format, url, encryptedFile)
}