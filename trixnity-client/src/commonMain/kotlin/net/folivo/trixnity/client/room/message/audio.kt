package net.folivo.trixnity.client.room.message

import io.ktor.http.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.core.model.events.m.room.AudioInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.utils.ByteArrayFlow

suspend fun MessageBuilder.audio(
    body: String,
    audio: ByteArrayFlow,
    format: String? = null,
    formattedBody: String? = null,
    fileName: String? = null,
    type: ContentType? = null,
    size: Long? = null,
    duration: Long? = null
) {
    val info: AudioInfo?
    val url: String?
    val encryptedFile: EncryptedFile?
    val isEncryptedRoom = roomService.getById(roomId).first()?.encrypted == true
    if (isEncryptedRoom) {
        encryptedFile = mediaService.prepareUploadEncryptedMedia(audio)
        url = null
        info = AudioInfo(
            duration = duration,
            mimeType = type?.toString(),
            size = size,
        )
    } else {
        encryptedFile = null
        url = mediaService.prepareUploadMedia(audio, type)
        info = AudioInfo(
            duration = duration,
            mimeType = type?.toString(),
            size = size,
        )
    }
    roomMessageBuilder(body, format, formattedBody) {
        RoomMessageEventContent.FileBased.Audio(
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