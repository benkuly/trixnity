package net.folivo.trixnity.client.room.message

import io.ktor.http.*
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.FileInfo
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.FileMessageEventContent

suspend fun MessageBuilder.file(
    body: String,
    file: ByteArray,
    type: ContentType,
    name: String? = null
) {
    val format: FileInfo?
    val url: String?
    val encryptedFile: EncryptedFile?
    if (isEncryptedRoom) {
        val (thumbnailFile, thumbnailInfo) = mediaManager.prepareUploadEncryptedThumbnail(file, type)
            ?: Pair(null, null)

        encryptedFile = mediaManager.prepareUploadEncryptedMedia(file)
        format = FileInfo(
            mimeType = type.toString(),
            size = file.size,
            thumbnailUrl = null,
            thumbnailFile = thumbnailFile,
            thumbnailInfo = thumbnailInfo
        )
        url = null
    } else {
        url = mediaManager.prepareUploadMedia(file, type)
        val (thumbnailUrl, thumbnailInfo) = mediaManager.prepareUploadThumbnail(file, type) ?: Pair(null, null)
        format = FileInfo(
            mimeType = type.toString(),
            size = file.size,
            thumbnailUrl = thumbnailUrl,
            thumbnailFile = null,
            thumbnailInfo = thumbnailInfo
        )
        encryptedFile = null
    }
    content = FileMessageEventContent(body, name, format, url, encryptedFile)
}