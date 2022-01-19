package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequest
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

/**
 * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mroommessage">matrix spec</a>
 */
@Serializable(with = RoomMessageEventContentSerializer::class)
sealed class RoomMessageEventContent : MessageEventContent {
    abstract val body: String

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mnotice">matrix spec</a>
     */
    @Serializable
    data class NoticeMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("format") val format: String? = null,
        @SerialName("formatted_body") val formattedBody: String? = null,
    ) : RoomMessageEventContent() {
        companion object {
            const val type = "m.notice"
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mtext">matrix spec</a>
     */
    @Serializable
    data class TextMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("format") val format: String? = null,
        @SerialName("formatted_body") val formattedBody: String? = null,
    ) : RoomMessageEventContent() {
        companion object {
            const val type = "m.text"
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#memote">matrix spec</a>
     */
    @Serializable
    data class EmoteMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("format") val format: String? = null,
        @SerialName("formatted_body") val formattedBody: String? = null,
    ) : RoomMessageEventContent() {
        companion object {
            const val type = "m.emote"
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mimage">matrix spec</a>
     */
    @Serializable
    data class ImageMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("info") val info: ImageInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null
    ) : RoomMessageEventContent() {
        companion object {
            const val type = "m.image"
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mfile">matrix spec</a>
     */
    @Serializable
    data class FileMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("filename") val fileName: String? = null,
        @SerialName("info") val info: FileInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null
    ) : RoomMessageEventContent() {
        companion object {
            const val type = "m.file"
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#maudio">matrix spec</a>
     */
    @Serializable
    data class AudioMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("info") val info: AudioInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null
    ) : RoomMessageEventContent() {
        companion object {
            const val type = "m.audio"
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mvideo">matrix spec</a>
     */
    @Serializable
    data class VideoMessageEventContent(
        @SerialName("body") override val body: String,
        @SerialName("info") val info: VideoInfo? = null,
        @SerialName("url") val url: String? = null,
        @SerialName("file") val file: EncryptedFile? = null
    ) : RoomMessageEventContent() {
        companion object {
            const val type = "m.video"
        }
    }

    @Serializable
    data class VerificationRequestMessageEventContent(
        @SerialName("from_device") override val fromDevice: String,
        @SerialName("to") val to: UserId,
        @SerialName("methods") override val methods: Set<VerificationMethod>,
        @SerialName("body") override val body: String = "Attempting verification request. (m.key.verification.request) Apparently your client doesn't support this.",
    ) : RoomMessageEventContent(), VerificationRequest {
        companion object {
            const val type = "m.key.verification.request"
        }
    }

    data class UnknownMessageEventContent(
        val type: String,
        override val body: String,
        val raw: JsonObject
    ) : RoomMessageEventContent()
}

object RoomMessageEventContentSerializer : KSerializer<RoomMessageEventContent> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomMessageEventContentSerializer")

    override fun deserialize(decoder: Decoder): RoomMessageEventContent {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        return when (val type = jsonObj["msgtype"]?.jsonPrimitive?.content) {
            RoomMessageEventContent.NoticeMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(NoticeMessageEventContentSerializer, jsonObj)
            RoomMessageEventContent.TextMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(TextMessageEventContentSerializer, jsonObj)
            RoomMessageEventContent.EmoteMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(EmoteMessageEventContentSerializer, jsonObj)
            RoomMessageEventContent.ImageMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(ImageMessageEventContentSerializer, jsonObj)
            RoomMessageEventContent.FileMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(FileMessageEventContentSerializer, jsonObj)
            RoomMessageEventContent.AudioMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(AudioMessageEventContentSerializer, jsonObj)
            RoomMessageEventContent.VideoMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(VideoMessageEventContentSerializer, jsonObj)
            RoomMessageEventContent.VerificationRequestMessageEventContent.type ->
                decoder.json.decodeFromJsonElement(VerificationRequestMessageEventContentSerializer, jsonObj)
            else -> {
                val body = jsonObj["body"]?.jsonPrimitive?.content
                requireNotNull(type)
                requireNotNull(body)
                RoomMessageEventContent.UnknownMessageEventContent(type, body, jsonObj)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: RoomMessageEventContent) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is RoomMessageEventContent.NoticeMessageEventContent ->
                encoder.json.encodeToJsonElement(NoticeMessageEventContentSerializer, value)
            is RoomMessageEventContent.TextMessageEventContent ->
                encoder.json.encodeToJsonElement(TextMessageEventContentSerializer, value)
            is RoomMessageEventContent.EmoteMessageEventContent ->
                encoder.json.encodeToJsonElement(EmoteMessageEventContentSerializer, value)
            is RoomMessageEventContent.ImageMessageEventContent ->
                encoder.json.encodeToJsonElement(ImageMessageEventContentSerializer, value)
            is RoomMessageEventContent.FileMessageEventContent ->
                encoder.json.encodeToJsonElement(FileMessageEventContentSerializer, value)
            is RoomMessageEventContent.AudioMessageEventContent ->
                encoder.json.encodeToJsonElement(AudioMessageEventContentSerializer, value)
            is RoomMessageEventContent.VideoMessageEventContent ->
                encoder.json.encodeToJsonElement(VideoMessageEventContentSerializer, value)
            is RoomMessageEventContent.VerificationRequestMessageEventContent ->
                encoder.json.encodeToJsonElement(VerificationRequestMessageEventContentSerializer, value)
            is RoomMessageEventContent.UnknownMessageEventContent -> value.raw
        }
        encoder.encodeJsonElement(jsonElement)
    }
}

object NoticeMessageEventContentSerializer :
    AddFieldsSerializer<RoomMessageEventContent.NoticeMessageEventContent>(
        RoomMessageEventContent.NoticeMessageEventContent.serializer(),
        "msgtype" to RoomMessageEventContent.NoticeMessageEventContent.type
    )

object TextMessageEventContentSerializer :
    AddFieldsSerializer<RoomMessageEventContent.TextMessageEventContent>(
        RoomMessageEventContent.TextMessageEventContent.serializer(),
        "msgtype" to RoomMessageEventContent.TextMessageEventContent.type
    )

object EmoteMessageEventContentSerializer :
    AddFieldsSerializer<RoomMessageEventContent.EmoteMessageEventContent>(
        RoomMessageEventContent.EmoteMessageEventContent.serializer(),
        "msgtype" to RoomMessageEventContent.EmoteMessageEventContent.type
    )

object ImageMessageEventContentSerializer :
    AddFieldsSerializer<RoomMessageEventContent.ImageMessageEventContent>(
        RoomMessageEventContent.ImageMessageEventContent.serializer(),
        "msgtype" to RoomMessageEventContent.ImageMessageEventContent.type
    )

object FileMessageEventContentSerializer :
    AddFieldsSerializer<RoomMessageEventContent.FileMessageEventContent>(
        RoomMessageEventContent.FileMessageEventContent.serializer(),
        "msgtype" to RoomMessageEventContent.FileMessageEventContent.type
    )

object AudioMessageEventContentSerializer :
    AddFieldsSerializer<RoomMessageEventContent.AudioMessageEventContent>(
        RoomMessageEventContent.AudioMessageEventContent.serializer(),
        "msgtype" to RoomMessageEventContent.AudioMessageEventContent.type
    )

object VideoMessageEventContentSerializer :
    AddFieldsSerializer<RoomMessageEventContent.VideoMessageEventContent>(
        RoomMessageEventContent.VideoMessageEventContent.serializer(),
        "msgtype" to RoomMessageEventContent.VideoMessageEventContent.type
    )

object VerificationRequestMessageEventContentSerializer :
    AddFieldsSerializer<RoomMessageEventContent.VerificationRequestMessageEventContent>(
        RoomMessageEventContent.VerificationRequestMessageEventContent.serializer(),
        "msgtype" to RoomMessageEventContent.VerificationRequestMessageEventContent.type
    )
