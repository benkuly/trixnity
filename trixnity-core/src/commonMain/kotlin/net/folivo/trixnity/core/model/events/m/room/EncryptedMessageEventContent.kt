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
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.Unknown
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroomencrypted">matrix spec</a>
 */
@Serializable(with = EncryptedMessageEventContentSerializer::class)
sealed interface EncryptedMessageEventContent : MessageEventContent {
    val algorithm: EncryptionAlgorithm

    @Serializable
    data class MegolmEncryptedMessageEventContent(
        @SerialName("ciphertext")
        val ciphertext: String,
        @Deprecated("see MSC3700")
        @SerialName("sender_key")
        val senderKey: Curve25519Key? = null,
        @Deprecated("see MSC3700")
        @SerialName("device_id")
        val deviceId: String? = null,
        @SerialName("session_id")
        val sessionId: String,
        @SerialName("m.relates_to")
        override val relatesTo: RelatesTo? = null,
        @SerialName("m.mentions")
        override val mentions: Mentions? = null,
        @SerialName("external_url")
        override val externalUrl: String? = null,
    ) : EncryptedMessageEventContent {
        @SerialName("algorithm")
        override val algorithm: Megolm = Megolm
    }

    data class Unknown(
        override val algorithm: EncryptionAlgorithm,
        val raw: JsonObject,
    ) : EncryptedMessageEventContent {
        override val relatesTo: RelatesTo? = null
        override val mentions: Mentions? = null
        override val externalUrl: String? = null
    }
}

object EncryptedMessageEventContentSerializer : KSerializer<EncryptedMessageEventContent> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EncryptedMessageEventContentSerializer")

    override fun deserialize(decoder: Decoder): EncryptedMessageEventContent {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        return when (val algorithm = decoder.json.decodeFromJsonElement<EncryptionAlgorithm>(
            jsonObj["algorithm"] ?: throw SerializationException("algorithm must not be null")
        )) {
            Megolm -> decoder.json.decodeFromJsonElement<MegolmEncryptedMessageEventContent>(jsonObj)
            Olm, is EncryptionAlgorithm.Unknown -> Unknown(algorithm, jsonObj)
        }
    }

    override fun serialize(encoder: Encoder, value: EncryptedMessageEventContent) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is MegolmEncryptedMessageEventContent -> encoder.json.encodeToJsonElement(value)
            is Unknown -> value.raw
        }
        encoder.encodeJsonElement(jsonElement)
    }
}