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
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.Unknown
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Megolm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.Olm
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroomencrypted">matrix spec</a>
 */
@Serializable(with = EncryptedToDeviceEventContentSerializer::class)
sealed interface EncryptedToDeviceEventContent : ToDeviceEventContent {
    val algorithm: EncryptionAlgorithm

    @Serializable
    data class OlmEncryptedToDeviceEventContent(
        @SerialName("ciphertext")
        val ciphertext: Map<String, CiphertextInfo>,
        @SerialName("sender_key")
        val senderKey: Curve25519KeyValue,
    ) : EncryptedToDeviceEventContent {
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
                            1 -> ORDINARY
                            else -> throw IllegalArgumentException("only 0 and 1 is allowed")
                        }
                    }
                }
            }
        }
    }

    data class Unknown(
        override val algorithm: EncryptionAlgorithm,
        val raw: JsonObject,
    ) : EncryptedToDeviceEventContent
}

object EncryptedToDeviceEventContentSerializer : KSerializer<EncryptedToDeviceEventContent> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EncryptedToDeviceEventContentSerializer")

    override fun deserialize(decoder: Decoder): EncryptedToDeviceEventContent {
        require(decoder is JsonDecoder)
        val jsonObj = decoder.decodeJsonElement().jsonObject
        return when (val algorithm = decoder.json.decodeFromJsonElement<EncryptionAlgorithm>(
            jsonObj["algorithm"] ?: JsonPrimitive("unknown")
        )) {
            Olm -> decoder.json.decodeFromJsonElement<OlmEncryptedToDeviceEventContent>(jsonObj)
            Megolm, is EncryptionAlgorithm.Unknown -> Unknown(algorithm, jsonObj)
        }
    }

    override fun serialize(encoder: Encoder, value: EncryptedToDeviceEventContent) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is OlmEncryptedToDeviceEventContent -> encoder.json.encodeToJsonElement(value)
            is Unknown -> value.raw
        }
        encoder.encodeJsonElement(jsonElement)
    }
}

object OlmMessageTypeSerializer :
    KSerializer<OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OlmMessageTypeSerializer", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType {
        return OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType.of(decoder.decodeInt())
    }

    override fun serialize(
        encoder: Encoder,
        value: OlmEncryptedToDeviceEventContent.CiphertextInfo.OlmMessageType
    ) {
        encoder.encodeInt(value.value)
    }
}