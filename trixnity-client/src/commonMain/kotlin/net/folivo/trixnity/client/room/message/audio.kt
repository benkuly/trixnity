package net.folivo.trixnity.client.room.message

import io.ktor.http.*
import net.folivo.trixnity.core.model.events.m.room.AudioInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.AudioMessageEventContent

suspend fun MessageBuilder.audio(
    body: String,
    audio: ByteArray,
    type: ContentType,
    duration: Int? = null
) {
    val format: AudioInfo?
    val url: String?
    val encryptedFile: EncryptedFile?
    if (isEncryptedRoom) {

        encryptedFile = mediaManager.prepareUploadEncryptedMedia(audio)
        format = AudioInfo(
            duration = duration,
            mimeType = type.toString(),
            size = audio.size,
        )
        url = null
    } else {
        url = mediaManager.prepareUploadMedia(audio, type)
        format = AudioInfo(
            duration = duration,
            mimeType = type.toString(),
            size = audio.size,
        )
        encryptedFile = null
    }
    content = AudioMessageEventContent(body, format, url, encryptedFile)
}