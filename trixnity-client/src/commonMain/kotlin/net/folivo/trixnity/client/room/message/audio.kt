package net.folivo.trixnity.client.room.message

import io.ktor.http.*
import net.folivo.trixnity.core.ByteFlow
import net.folivo.trixnity.core.TrixnityDsl
import net.folivo.trixnity.core.model.events.m.room.AudioInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.AudioMessageEventContent

@TrixnityDsl
suspend fun MessageBuilder.audio(
    body: String,
    audio: ByteFlow,
    type: ContentType,
    size: Int? = null,
    duration: Int? = null
) {
    val format: AudioInfo?
    val url: String?
    val encryptedFile: EncryptedFile?
    if (isEncryptedRoom) {

        encryptedFile = mediaService.prepareUploadEncryptedMedia(audio)
        format = AudioInfo(
            duration = duration,
            mimeType = type.toString(),
            size = size,
        )
        url = null
    } else {
        url = mediaService.prepareUploadMedia(audio, type)
        format = AudioInfo(
            duration = duration,
            mimeType = type.toString(),
            size = size,
        )
        encryptedFile = null
    }
    content = AudioMessageEventContent(body, format, url, encryptedFile)
}