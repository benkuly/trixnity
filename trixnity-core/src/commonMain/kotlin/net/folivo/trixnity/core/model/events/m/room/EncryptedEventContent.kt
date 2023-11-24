package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent.OlmEncryptedEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mroomencrypted">matrix spec</a>
 */
sealed interface EncryptedEventContent {
    val algorithm: EncryptionAlgorithm

    @Serializable
    data class MegolmEncryptedEventContent(
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
    ) : EncryptedEventContent, MessageEventContent {
        @SerialName("algorithm")
        override val algorithm: Megolm = Megolm
    }

    @Serializable
    data class OlmEncryptedEventContent(
        @SerialName("ciphertext")
        val ciphertext: Map<String, CiphertextInfo>,
        @SerialName("sender_key")
        val senderKey: Curve25519Key,
    ) : EncryptedEventContent, ToDeviceEventContent {
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