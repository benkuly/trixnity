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
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequest as IVerificationRequest

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mroommessage">matrix spec</a>
 */
@Serializable(with = RoomMessageEventContentSerializer::class)
sealed interface RoomMessageEventContent : MessageEventContent {
    val body: String

    sealed interface TextBased : RoomMessageEventContent {
        val format: String?
        val formattedBody: String?

        /**
         * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mnotice">matrix spec</a>
         */
        @Serializable
        data class Notice(
            @SerialName("body") override val body: String,
            @SerialName("format") override val format: String? = null,
            @SerialName("formatted_body") override val formattedBody: String? = null,
            @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
            @SerialName("m.mentions") override val mentions: Mentions? = null,
            @SerialName("external_url") override val externalUrl: String? = null,
        ) : TextBased {
            @SerialName("msgtype")
            val type = "m.notice"

            companion object {
                const val type = "m.notice"
            }
        }

        /**
         * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mtext">matrix spec</a>
         */
        @Serializable
        data class Text(
            @SerialName("body") override val body: String,
            @SerialName("format") override val format: String? = null,
            @SerialName("formatted_body") override val formattedBody: String? = null,
            @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
            @SerialName("m.mentions") override val mentions: Mentions? = null,
            @SerialName("external_url") override val externalUrl: String? = null,
        ) : TextBased {
            @SerialName("msgtype")
            val type = "m.text"

            companion object {
                const val type = "m.text"
            }
        }

        /**
         * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#memote">matrix spec</a>
         */
        @Serializable
        data class Emote(
            @SerialName("body") override val body: String,
            @SerialName("format") override val format: String? = null,
            @SerialName("formatted_body") override val formattedBody: String? = null,
            @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
            @SerialName("m.mentions") override val mentions: Mentions? = null,
            @SerialName("external_url") override val externalUrl: String? = null,
        ) : TextBased {
            @SerialName("msgtype")
            val type = "m.emote"

            companion object {
                const val type = "m.emote"
            }
        }
    }

    sealed interface FileBased : RoomMessageEventContent {
        val url: String?
        val file: EncryptedFile?
        val info: FileBasedInfo?

        /**
         * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mimage">matrix spec</a>
         */
        @Serializable
        data class Image(
            @SerialName("body") override val body: String,
            @SerialName("info") override val info: ImageInfo? = null,
            @SerialName("url") override val url: String? = null,
            @SerialName("file") override val file: EncryptedFile? = null,
            @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
            @SerialName("m.mentions") override val mentions: Mentions? = null,
            @SerialName("external_url") override val externalUrl: String? = null,
        ) : FileBased {
            @SerialName("msgtype")
            val type = "m.image"

            companion object {
                const val type = "m.image"
            }
        }

        /**
         * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mfile">matrix spec</a>
         */
        @Serializable
        data class File(
            @SerialName("body") override val body: String,
            @SerialName("filename") val fileName: String? = null,
            @SerialName("info") override val info: FileInfo? = null,
            @SerialName("url") override val url: String? = null,
            @SerialName("file") override val file: EncryptedFile? = null,
            @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
            @SerialName("m.mentions") override val mentions: Mentions? = null,
            @SerialName("external_url") override val externalUrl: String? = null,
        ) : FileBased {
            @SerialName("msgtype")
            val type = "m.file"

            companion object {
                const val type = "m.file"
            }
        }

        /**
         * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#maudio">matrix spec</a>
         */
        @Serializable
        data class Audio(
            @SerialName("body") override val body: String,
            @SerialName("info") override val info: AudioInfo? = null,
            @SerialName("url") override val url: String? = null,
            @SerialName("file") override val file: EncryptedFile? = null,
            @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
            @SerialName("m.mentions") override val mentions: Mentions? = null,
            @SerialName("external_url") override val externalUrl: String? = null,
        ) : FileBased {
            @SerialName("msgtype")
            val type = "m.audio"

            companion object {
                const val type = "m.audio"
            }
        }

        /**
         * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mvideo">matrix spec</a>
         */
        @Serializable
        data class Video(
            @SerialName("body") override val body: String,
            @SerialName("info") override val info: VideoInfo? = null,
            @SerialName("url") override val url: String? = null,
            @SerialName("file") override val file: EncryptedFile? = null,
            @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
            @SerialName("m.mentions") override val mentions: Mentions? = null,
            @SerialName("external_url") override val externalUrl: String? = null,
        ) : FileBased {
            @SerialName("msgtype")
            val type = "m.video"

            companion object {
                const val type = "m.video"
            }
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mlocation">matrix spec</a>
     */
    @Serializable
    data class Location(
        @SerialName("body") override val body: String,
        @SerialName("geo_uri") val geoUri: String,
        @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
        @SerialName("m.mentions") override val mentions: Mentions? = null,
        @SerialName("external_url") override val externalUrl: String? = null,
    ) : RoomMessageEventContent {
        @SerialName("msgtype")
        val type = "m.location"

        companion object {
            const val type = "m.location"
        }
    }

    @Serializable
    data class VerificationRequest(
        @SerialName("from_device") override val fromDevice: String,
        @SerialName("to") val to: UserId,
        @SerialName("methods") override val methods: Set<VerificationMethod>,
        @SerialName("body") override val body: String = "Attempting verification request (m.key.verification.request). Apparently your client doesn't support this.",
        @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
        @SerialName("m.mentions") override val mentions: Mentions? = null,
        @SerialName("external_url") override val externalUrl: String? = null,
    ) : RoomMessageEventContent, IVerificationRequest {
        @SerialName("msgtype")
        val type = "m.key.verification.request"

        companion object {
            const val type = "m.key.verification.request"
        }
    }

    data class Unknown(
        val type: String,
        override val body: String,
        val raw: JsonObject,
        override val relatesTo: RelatesTo? = null,
        override val mentions: Mentions? = null,
        override val externalUrl: String? = null,
    ) : RoomMessageEventContent
}

object RoomMessageEventContentSerializer : KSerializer<RoomMessageEventContent> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomMessageEventContentSerializer")

    override fun deserialize(decoder: Decoder): RoomMessageEventContent {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        return when (val type = (jsonObj["msgtype"] as? JsonPrimitive)?.content) {
            TextBased.Text.type -> decoder.json.decodeFromJsonElement<TextBased.Text>(jsonObj)
            TextBased.Notice.type -> decoder.json.decodeFromJsonElement<TextBased.Notice>(jsonObj)
            TextBased.Emote.type -> decoder.json.decodeFromJsonElement<TextBased.Emote>(jsonObj)
            FileBased.Image.type -> decoder.json.decodeFromJsonElement<FileBased.Image>(jsonObj)
            FileBased.File.type -> decoder.json.decodeFromJsonElement<FileBased.File>(jsonObj)
            FileBased.Audio.type -> decoder.json.decodeFromJsonElement<FileBased.Audio>(jsonObj)
            FileBased.Video.type -> decoder.json.decodeFromJsonElement<FileBased.Video>(jsonObj)
            VerificationRequest.type ->
                decoder.json.decodeFromJsonElement<VerificationRequest>(jsonObj)

            else -> {
                val body = (jsonObj["body"] as? JsonPrimitive)?.content
                val relatesTo: RelatesTo? =
                    (jsonObj["m.relates_to"] as? JsonObject)?.let { decoder.json.decodeFromJsonElement(it) }
                val mentions: Mentions? =
                    (jsonObj["m.mentions"] as? JsonObject)?.let { decoder.json.decodeFromJsonElement(it) }
                val externalUrl: String? =
                    (jsonObj["external_url"] as? JsonObject)?.let { decoder.json.decodeFromJsonElement(it) }
                if (type == null) throw SerializationException("msgtype must not be null")
                if (body == null) throw SerializationException("body must not be null")
                Unknown(type, body, jsonObj, relatesTo, mentions, externalUrl)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: RoomMessageEventContent) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is TextBased.Notice -> encoder.json.encodeToJsonElement(value)
            is TextBased.Text -> encoder.json.encodeToJsonElement(value)
            is TextBased.Emote -> encoder.json.encodeToJsonElement(value)
            is FileBased.Image -> encoder.json.encodeToJsonElement(value)
            is FileBased.File -> encoder.json.encodeToJsonElement(value)
            is FileBased.Audio -> encoder.json.encodeToJsonElement(value)
            is FileBased.Video -> encoder.json.encodeToJsonElement(value)
            is Location -> encoder.json.encodeToJsonElement(value)
            is VerificationRequest -> encoder.json.encodeToJsonElement(value)
            is Unknown -> value.raw
        }
        encoder.encodeJsonElement(jsonElement)
    }
}

fun RoomMessageEventContent.getFormattedBody(): String? = when (this) {
    is TextBased.Text -> formattedBody
    is TextBased.Notice -> formattedBody
    is TextBased.Emote -> formattedBody
    is FileBased.Audio,
    is FileBased.File,
    is FileBased.Image,
    is Location,
    is Unknown,
    is VerificationRequest,
    is FileBased.Video -> null
}

val RoomMessageEventContent.bodyWithoutFallback: String
    get() =
        if (this.relatesTo?.replyTo != null) {
            body.lineSequence()
                .dropWhile { it.startsWith("> ") }
                .dropWhile { it == "" }
                .joinToString("\n")
        } else body

val TextBased.Text.formattedBodyWithoutFallback: String?
    get() = formattedBody?.removeFallbackFromFormattedBody()

val TextBased.Notice.formattedBodyWithoutFallback: String?
    get() = formattedBody?.removeFallbackFromFormattedBody()

val TextBased.Emote.formattedBodyWithoutFallback: String?
    get() = formattedBody?.removeFallbackFromFormattedBody()

private fun String.removeFallbackFromFormattedBody(): String =
    substringAfter("</mx-reply>").removePrefix("\n")