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
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm.*
import net.folivo.trixnity.core.model.keys.Key.Curve25519Key
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

/**
 * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mroomencrypted">matrix spec</a>
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
        @SerialName("algorithm")
        override val algorithm: Megolm = Megolm
    ) : EncryptedEventContent()

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
        @SerialName("algorithm")
        override val algorithm: Olm = Olm
    ) : EncryptedEventContent() {
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
        val ciphertext: JsonElement,
        @SerialName("sender_key")
        override val senderKey: Curve25519Key,
        @SerialName("device_id")
        override val deviceId: String? = null,
        @SerialName("session_id")
        override val sessionId: String? = null,
        @SerialName("algorithm")
        override val algorithm: Unknown
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
            Olm ->
                decoder.json.decodeFromJsonElement<EncryptedEventContent.OlmEncryptedEventContent>(jsonObj)
            Megolm ->
                decoder.json.decodeFromJsonElement<EncryptedEventContent.MegolmEncryptedEventContent>(jsonObj)
            else ->
                decoder.json.decodeFromJsonElement<EncryptedEventContent.UnknownEncryptedEventContent>(jsonObj)
        }
    }

    override fun serialize(encoder: Encoder, value: EncryptedEventContent) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is EncryptedEventContent.OlmEncryptedEventContent ->
                encoder.json.encodeToJsonElement(
                    AddFieldsSerializer(OlmEncryptedEventContentSerializer, "algorithm" to Olm.name), value
                )
            is EncryptedEventContent.MegolmEncryptedEventContent ->
                encoder.json.encodeToJsonElement(
                    AddFieldsSerializer(MegolmEncryptedEventContentSerializer, "algorithm" to Megolm.name), value
                )
            is EncryptedEventContent.UnknownEncryptedEventContent -> encoder.json.encodeToJsonElement(value)
        }
        encoder.encodeJsonElement(jsonElement)
    }
}

object MegolmEncryptedEventContentSerializer :
    AddFieldsSerializer<EncryptedEventContent.MegolmEncryptedEventContent>(
        EncryptedEventContent.MegolmEncryptedEventContent.serializer(),
        "algorithm" to Megolm.name
    )

object OlmEncryptedEventContentSerializer :
    AddFieldsSerializer<EncryptedEventContent.OlmEncryptedEventContent>(
        EncryptedEventContent.OlmEncryptedEventContent.serializer(),
        "algorithm" to Olm.name
    )

object OlmMessageTypeSerializer :
    KSerializer<EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo.OlmMessageType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OlmMessageTypeSerializer", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo.OlmMessageType {
        return EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo.OlmMessageType.of(decoder.decodeInt())
    }

    override fun serialize(
        encoder: Encoder,
        value: EncryptedEventContent.OlmEncryptedEventContent.CiphertextInfo.OlmMessageType
    ) {
        encoder.encodeInt(value.value)
    }
}