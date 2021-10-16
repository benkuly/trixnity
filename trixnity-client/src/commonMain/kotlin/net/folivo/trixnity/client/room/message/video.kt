package net.folivo.trixnity.client.room.message

import io.ktor.http.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VideoMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.VideoInfo

suspend fun MessageBuilder.video(
    body: String,
    video: ByteArray,
    type: ContentType,
    height: Int? = null,
    width: Int? = null,
    duration: Int? = null
) {
    val format: VideoInfo?
    val url: String?
    val encryptedFile: EncryptedFile?
    if (isEncryptedRoom) {
        val (thumbnailFile, thumbnailInfo) = mediaManager.prepareUploadEncryptedThumbnail(video, type)
            ?: Pair(null, null)

        encryptedFile = mediaManager.prepareUploadEncryptedMedia(video)
        format = VideoInfo(
            duration = duration,
            height = height,
            width = width,
            mimeType = type.toString(),
            size = video.size,
            thumbnailUrl = null,
            thumbnailFile = thumbnailFile,
            thumbnailInfo = thumbnailInfo
        )
        url = null
    } else {
        url = mediaManager.prepareUploadMedia(video, type)
        val (thumbnailUrl, thumbnailInfo) = mediaManager.prepareUploadThumbnail(video, type) ?: Pair(null, null)
        format = VideoInfo(
            duration = duration,
            height = height,
            width = width,
            mimeType = type.toString(),
            size = video.size,
            thumbnailUrl = thumbnailUrl,
            thumbnailFile = null,
            thumbnailInfo = thumbnailInfo
        )
        encryptedFile = null
    }
    content = VideoMessageEventContent(body, format, url, encryptedFile)
}