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
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequest as IVerificationRequest

/**
 * Matrix room message event content
 *
 * Room messages have "type": "m.room.message".
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroommessage">matrix spec</a>
 */
@Serializable(with = RoomMessageEventContent.Serializer::class)
sealed interface RoomMessageEventContent : MessageEventContent {
    val body: String
    val format: String?
    val formattedBody: String?
    val type: String

    sealed interface TextBased : RoomMessageEventContent {
        /**
         * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mnotice">matrix spec</a>
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
            override val type = TYPE

            companion object {
                const val TYPE = "m.notice"
            }

            override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)
        }

        /**
         * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mtext">matrix spec</a>
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
            override val type = TYPE

            companion object {
                const val TYPE = "m.text"
            }

            override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)
        }

        /**
         * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#memote">matrix spec</a>
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
            override val type = TYPE

            companion object {
                const val TYPE = "m.emote"
            }

            override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)
        }
    }

    sealed interface FileBased : RoomMessageEventContent {
        val url: String?
        val file: EncryptedFile?
        val info: FileBasedInfo?
        val fileName: String?

        /**
         * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mimage">matrix spec</a>
         */
        @Serializable
        data class Image(
            @SerialName("body") override val body: String,
            @SerialName("format") override val format: String? = null,
            @SerialName("formatted_body") override val formattedBody: String? = null,
            @SerialName("filename") override val fileName: String? = null,
            @SerialName("info") override val info: ImageInfo? = null,
            @SerialName("url") override val url: String? = null,
            @SerialName("file") override val file: EncryptedFile? = null,
            @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
            @SerialName("m.mentions") override val mentions: Mentions? = null,
            @SerialName("external_url") override val externalUrl: String? = null,
        ) : FileBased {
            @SerialName("msgtype")
            override val type = TYPE

            companion object {
                const val TYPE = "m.image"
            }

            override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)
        }

        /**
         * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mfile">matrix spec</a>
         */
        @Serializable
        data class File(
            @SerialName("body") override val body: String,
            @SerialName("format") override val format: String? = null,
            @SerialName("formatted_body") override val formattedBody: String? = null,
            @SerialName("filename") override val fileName: String? = null,
            @SerialName("info") override val info: FileInfo? = null,
            @SerialName("url") override val url: String? = null,
            @SerialName("file") override val file: EncryptedFile? = null,
            @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
            @SerialName("m.mentions") override val mentions: Mentions? = null,
            @SerialName("external_url") override val externalUrl: String? = null,
        ) : FileBased {
            @SerialName("msgtype")
            override val type = TYPE

            companion object {
                const val TYPE = "m.file"
            }

            override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)
        }

        /**
         * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#maudio">matrix spec</a>
         */
        @Serializable
        data class Audio(
            @SerialName("body") override val body: String,
            @SerialName("format") override val format: String? = null,
            @SerialName("formatted_body") override val formattedBody: String? = null,
            @SerialName("filename") override val fileName: String? = null,
            @SerialName("info") override val info: AudioInfo? = null,
            @SerialName("url") override val url: String? = null,
            @SerialName("file") override val file: EncryptedFile? = null,
            @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
            @SerialName("m.mentions") override val mentions: Mentions? = null,
            @SerialName("external_url") override val externalUrl: String? = null,
        ) : FileBased {
            @SerialName("msgtype")
            override val type = TYPE

            companion object {
                const val TYPE = "m.audio"
            }

            override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)
        }

        /**
         * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mvideo">matrix spec</a>
         */
        @Serializable
        data class Video(
            @SerialName("body") override val body: String,
            @SerialName("format") override val format: String? = null,
            @SerialName("formatted_body") override val formattedBody: String? = null,
            @SerialName("filename") override val fileName: String? = null,
            @SerialName("info") override val info: VideoInfo? = null,
            @SerialName("url") override val url: String? = null,
            @SerialName("file") override val file: EncryptedFile? = null,
            @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
            @SerialName("m.mentions") override val mentions: Mentions? = null,
            @SerialName("external_url") override val externalUrl: String? = null,
        ) : FileBased {
            @SerialName("msgtype")
            override val type = TYPE

            companion object {
                const val TYPE = "m.video"
            }

            override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mlocation">matrix spec</a>
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
        override val type = TYPE

        override val format: String? = null
        override val formattedBody: String? = null

        companion object {
            const val TYPE = "m.location"
        }

        override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)
    }

    @Serializable
    data class VerificationRequest(
        @SerialName("from_device") override val fromDevice: String,
        @SerialName("to") val to: UserId,
        @SerialName("methods") override val methods: Set<VerificationMethod>,
        @SerialName("body") override val body: String = "Attempting verification request (m.key.verification.request). Apparently your client doesn't support this.",
        @SerialName("format") override val format: String? = null,
        @SerialName("formatted_body") override val formattedBody: String? = null,
        @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
        @SerialName("m.mentions") override val mentions: Mentions? = null,
        @SerialName("external_url") override val externalUrl: String? = null,
    ) : RoomMessageEventContent, IVerificationRequest {
        @SerialName("msgtype")
        override val type = TYPE

        companion object {
            const val TYPE = "m.key.verification.request"
        }

        override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)
    }

    data class Unknown(
        override val type: String,
        override val body: String,
        val raw: JsonObject,
        override val format: String? = null,
        override val formattedBody: String? = null,
        override val relatesTo: RelatesTo? = null,
        override val mentions: Mentions? = null,
        override val externalUrl: String? = null,
    ) : RoomMessageEventContent {
        override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo)
    }

    object Serializer : KSerializer<RoomMessageEventContent> {

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RoomMessageEventContent")

        override fun deserialize(decoder: Decoder): RoomMessageEventContent {
            require(decoder is JsonDecoder)
            val jsonObj = decoder.decodeJsonElement().jsonObject
            return when (val type = (jsonObj["msgtype"] as? JsonPrimitive)?.contentOrNull) {
                TextBased.Text.TYPE -> decoder.json.decodeFromJsonElement<TextBased.Text>(jsonObj)
                TextBased.Notice.TYPE -> decoder.json.decodeFromJsonElement<TextBased.Notice>(jsonObj)
                TextBased.Emote.TYPE -> decoder.json.decodeFromJsonElement<TextBased.Emote>(jsonObj)
                FileBased.Image.TYPE -> decoder.json.decodeFromJsonElement<FileBased.Image>(jsonObj)
                FileBased.File.TYPE -> decoder.json.decodeFromJsonElement<FileBased.File>(jsonObj)
                FileBased.Audio.TYPE -> decoder.json.decodeFromJsonElement<FileBased.Audio>(jsonObj)
                FileBased.Video.TYPE -> decoder.json.decodeFromJsonElement<FileBased.Video>(jsonObj)
                Location.TYPE -> decoder.json.decodeFromJsonElement<Location>(jsonObj)
                VerificationRequest.TYPE ->
                    decoder.json.decodeFromJsonElement<VerificationRequest>(jsonObj)

                else -> {
                    if (type == null) throw SerializationException("msgtype must not be null")
                    val body = (jsonObj["body"] as? JsonPrimitive)?.contentOrNull
                        ?: throw SerializationException("body must not be null")
                    val format = (jsonObj["format"] as? JsonPrimitive)?.contentOrNull
                    val formattedBody = (jsonObj["formatted_body"] as? JsonPrimitive)?.contentOrNull
                    val relatesTo: RelatesTo? =
                        (jsonObj["m.relates_to"] as? JsonObject)?.let { decoder.json.decodeFromJsonElement(it) }
                    val mentions: Mentions? =
                        (jsonObj["m.mentions"] as? JsonObject)?.let { decoder.json.decodeFromJsonElement(it) }
                    val externalUrl: String? = (jsonObj["external_url"] as? JsonPrimitive)?.contentOrNull
                    Unknown(type, body, jsonObj, format, formattedBody, relatesTo, mentions, externalUrl)
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
}

val RoomMessageEventContent.bodyWithoutFallback: String
    get() =
        if (this.relatesTo?.replyTo != null) {
            body.lineSequence()
                .dropWhile { it.startsWith("> ") }
                .dropWhile { it == "" }
                .joinToString("\n")
        } else body

val RoomMessageEventContent.formattedBodyWithoutFallback: String?
    get() =
        if (this.relatesTo?.replyTo != null)
            formattedBody?.substringAfterLast("</mx-reply>")?.removePrefix("\n")
        else formattedBody