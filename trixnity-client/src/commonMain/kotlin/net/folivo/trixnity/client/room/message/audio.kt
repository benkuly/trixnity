package net.folivo.trixnity.client.room.message

import io.ktor.http.*
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.AudioInfo
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.AudioMessageEventContent
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.TrixnityDsl

@TrixnityDsl
suspend fun MessageBuilder.audio(
    body: String,
    audio: ByteArrayFlow,
    type: ContentType? = null,
    size: Int? = null,
    duration: Int? = null
) {
    val format: AudioInfo?
    val url: String?
    val encryptedFile: EncryptedFile?
    val isEncryptedRoom = roomService.getById(roomId).first()?.encrypted == true
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
    contentBuilder = { relatesTo, mentions, newContentMentions ->
        when (relatesTo) {
            is RelatesTo.Replace -> AudioMessageEventContent(
                body = "* $body",
                info = format,
                url = url,
                file = encryptedFile,
                relatesTo = relatesTo.copy(
                    newContent = AudioMessageEventContent(
                        body = body,
                        info = format,
                        url = url,
                        file = encryptedFile,
                        mentions = newContentMentions,
                    )
                ),
                mentions = mentions,
            )

            else -> AudioMessageEventContent(
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