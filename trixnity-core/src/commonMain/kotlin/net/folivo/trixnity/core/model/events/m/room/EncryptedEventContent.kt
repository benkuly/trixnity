package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.*
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.*
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mroomencrypted">matrix spec</a>
 */
@Serializable(with = EncryptedEventContentSerializer::class)
sealed class EncryptedEventContent : MessageEventContent, ToDeviceEventContent {
    abstract val senderKey: Curve25519Key
    abstract val deviceId: String?
    abstract val sessionId: String?
    abstract val algorithm: EncryptionAlgorithm

    @Serializable
    data class MegolmEncryptedEventContent(
        @SerialName("ciphertext")
        val ciphertext: String,
        @SerialName("sender_key")
        override val senderKey: Curve25519Key,
        @SerialName("device_id")
        override val deviceId: String,
        @SerialName("session_id")
        override val sessionId: String,
        @SerialName("m.relates_to")
        override val relatesTo: RelatesTo? = null,
    ) : EncryptedEventContent() {
        @SerialName("algorithm")
        override val algorithm: Megolm = Megolm
    }

    @Serializable
    data class OlmEncryptedEventContent(
        @SerialName("ciphertext")
        val ciphertext: Map<String, CiphertextInfo>,
        @SerialName("sender_key")
        override val senderKey: Curve25519Key,
        @SerialName("device_id")
        override val deviceId: String? = null,
        @SerialName("session_id")
        override val sessionId: String? = null,
        @SerialName("m.relates_to")
        override val relatesTo: RelatesTo? = null
    ) : EncryptedEventContent() {
        @SerialName("algorithm")
        override val algorithm: Olm = Olm

        @Serializable
        data class CiphertextInfo(
            @SerialName("body")
            val body: String,
            @SerialName("type")
            val type: OlmMessageType
        ) {
            @Serializable(with = OlmMessageTypeSerializer::class)
            enum class OlmMessageType(val value: Int) {
                INITIAL_PRE_KEY(0),
                ORDINARY(1);

                companion object {
                    fun of(value: Int): OlmMessageType {
                        return when (value) {
                            0 -> INITIAL_PRE_KEY
                            else -> ORDINARY
                        }
                    }
                }
            }
        }
    }

    @Serializable
    data class UnknownEncryptedEventContent(
        @SerialName("ciphertext")
        val ciphertext: JsonElement? = null,
        @SerialName("sender_key")
        override val senderKey: Curve25519Key,
        @SerialName("device_id")
        override val deviceId: String? = null,
        @SerialName("session_id")
        override val sessionId: String? = null,
        @SerialName("m.relates_to")
        override val relatesTo: RelatesTo? = null,
        @SerialName("algorithm")
        override val algorithm: Unknown,
    ) : EncryptedEventContent()
}

object EncryptedEventContentSerializer : KSerializer<EncryptedEventContent> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EncryptedEventContentSerializer")

    override fun deserialize(decoder: Decoder): EncryptedEventContent {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        return when (decoder.json.decodeFromJsonElement<EncryptionAlgorithm>(
            jsonObj["algorithm"] ?: JsonPrimitive("unknown")
        )) {
            Olm -> decoder.json.decodeFromJsonElement<OlmEncryptedEventContent>(jsonObj)
            Megolm -> decoder.json.decodeFromJsonElement<MegolmEncryptedEventContent>(jsonObj)
            else -> decoder.json.decodeFromJsonElement<UnknownEncryptedEventContent>(jsonObj)
        }
    }

    override fun serialize(encoder: Encoder, value: EncryptedEventContent) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is OlmEncryptedEventContent -> encoder.json.encodeToJsonElement(value)
            is MegolmEncryptedEventContent -> encoder.json.encodeToJsonElement(value)
            is UnknownEncryptedEventContent -> encoder.json.encodeToJsonElement(value)
        }
        encoder.encodeJsonElement(jsonElement)
    }
}

object OlmMessageTypeSerializer :
    KSerializer<OlmEncryptedEventContent.CiphertextInfo.OlmMessageType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OlmMessageTypeSerializer", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): OlmEncryptedEventContent.CiphertextInfo.OlmMessageType {
        return OlmEncryptedEventContent.CiphertextInfo.OlmMessageType.of(decoder.decodeInt())
    }

    override fun serialize(
        encoder: Encoder,
        value: OlmEncryptedEventContent.CiphertextInfo.OlmMessageType
    ) {
        encoder.encodeInt(value.value)
    }
}