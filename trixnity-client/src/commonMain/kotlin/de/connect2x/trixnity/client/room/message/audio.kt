package de.connect2x.trixnity.client.room.message

import io.ktor.http.*
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.core.model.events.m.room.AudioInfo
import de.connect2x.trixnity.core.model.events.m.room.EncryptedFile
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.utils.ByteArrayFlow

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