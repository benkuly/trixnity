package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequest
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.*

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mroommessage">matrix spec</a>
 */
@Serializable(with = RoomMessageEventContentSerializer::class)
sealed class RoomMessageEventContent : MessageEventContent {
    abstract val body: String

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mnotice">matrix spec</a>
     */
    @Serializable
    data class NoticeMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("format") val format: String? = null,
        @SerialName("formatted_body") val formattedBody: String? = null,
        @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    ) : RoomMessageEventContent() {
        @SerialName("msgtype")
        val type = "m.notice"

        companion object {
            const val type = "m.notice"
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mtext">matrix spec</a>
     */
    @Serializable
    data class TextMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("format") val format: String? = null,
        @SerialName("formatted_body") val formattedBody: String? = null,
        @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    ) : RoomMessageEventContent() {
        @SerialName("msgtype")
        val type = "m.text"

        companion object {
            const val type = "m.text"
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#memote">matrix spec</a>
     */
    @Serializable
    data class EmoteMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("format") val format: String? = null,
        @SerialName("formatted_body") val formattedBody: String? = null,
        @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    ) : RoomMessageEventContent() {
        @SerialName("msgtype")
        val type = "m.emote"

        companion object {
            const val type = "m.emote"
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mimage">matrix spec</a>
     */
    @Serializable
    data class ImageMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("info") val info: ImageInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null,
        @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    ) : RoomMessageEventContent() {
        @SerialName("msgtype")
        val type = "m.image"

        companion object {
            const val type = "m.image"
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mfile">matrix spec</a>
     */
    @Serializable
    data class FileMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("filename") val fileName: String? = null,
        @SerialName("info") val info: FileInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null,
        @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    ) : RoomMessageEventContent() {
        @SerialName("msgtype")
        val type = "m.file"

        companion object {
            const val type = "m.file"
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#maudio">matrix spec</a>
     */
    @Serializable
    data class AudioMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("info") val info: AudioInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null,
        @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    ) : RoomMessageEventContent() {
        @SerialName("msgtype")
        val type = "m.audio"

        companion object {
            const val type = "m.audio"
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mvideo">matrix spec</a>
     */
    @Serializable
    data class VideoMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("info") val info: VideoInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null,
        @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    ) : RoomMessageEventContent() {
        @SerialName("msgtype")
        val type = "m.video"

        companion object {
            const val type = "m.video"
        }
    }

    @Serializable
    data class VerificationRequestMessageEventContent(
        @SerialName("from_device") override val fromDevice: String,
        @SerialName("to") val to: UserId,
        @SerialName("methods") override val methods: Set<VerificationMethod>,
        @SerialName("body") override val body: String = "Attempting verification request (m.key.verification.request). Apparently your client doesn't support this.",
        @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    ) : RoomMessageEventContent(), VerificationRequest {
        @SerialName("msgtype")
        val type = "m.key.verification.request"

        companion object {
            const val type = "m.key.verification.request"
        }
    }

    data class UnknownRoomMessageEventContent(
        val type: String,
        override val body: String,
        val raw: JsonObject,
        override val relatesTo: RelatesTo? = null,
    ) : RoomMessageEventContent()
}

object RoomMessageEventContentSerializer : KSerializer<RoomMessageEventContent> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomMessageEventContentSerializer")

    override fun deserialize(decoder: Decoder): RoomMessageEventContent {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        return when (val type = jsonObj["msgtype"]?.jsonPrimitive?.content) {
            NoticeMessageEventContent.type -> decoder.json.decodeFromJsonElement<NoticeMessageEventContent>(jsonObj)
            TextMessageEventContent.type -> decoder.json.decodeFromJsonElement<TextMessageEventContent>(jsonObj)
            EmoteMessageEventContent.type -> decoder.json.decodeFromJsonElement<EmoteMessageEventContent>(jsonObj)
            ImageMessageEventContent.type -> decoder.json.decodeFromJsonElement<ImageMessageEventContent>(jsonObj)
            FileMessageEventContent.type -> decoder.json.decodeFromJsonElement<FileMessageEventContent>(jsonObj)
            AudioMessageEventContent.type -> decoder.json.decodeFromJsonElement<AudioMessageEventContent>(jsonObj)
            VideoMessageEventContent.type -> decoder.json.decodeFromJsonElement<VideoMessageEventContent>(jsonObj)
            VerificationRequestMessageEventContent.type ->
                decoder.json.decodeFromJsonElement<VerificationRequestMessageEventContent>(jsonObj)
            else -> {
                val body = jsonObj["body"]?.jsonPrimitive?.content
                val relatesTo: RelatesTo? =
                    jsonObj["m.relates_to"]?.jsonObject?.let { decoder.json.decodeFromJsonElement(it) }
                if (type == null) throw SerializationException("msgtype must not be null")
                if (body == null) throw SerializationException("body must not be null")
                UnknownRoomMessageEventContent(type, body, jsonObj, relatesTo)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: RoomMessageEventContent) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is NoticeMessageEventContent -> encoder.json.encodeToJsonElement(value)
            is TextMessageEventContent -> encoder.json.encodeToJsonElement(value)
            is EmoteMessageEventContent -> encoder.json.encodeToJsonElement(value)
            is ImageMessageEventContent -> encoder.json.encodeToJsonElement(value)
            is FileMessageEventContent -> encoder.json.encodeToJsonElement(value)
            is AudioMessageEventContent -> encoder.json.encodeToJsonElement(value)
            is VideoMessageEventContent -> encoder.json.encodeToJsonElement(value)
            is VerificationRequestMessageEventContent -> encoder.json.encodeToJsonElement(value)
            is UnknownRoomMessageEventContent -> value.raw
        }
        encoder.encodeJsonElement(jsonElement)
    }
}