package net.folivo.trixnity.core.model.events.m.secretstorage

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.crypto.SecretStorageAlgorithm
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.serialization.AddFieldsSerializer

/**
 * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#key-storage">matrix spec</a>
 */
@Serializable(with = SecretStorageKeyEventContentSerializer::class)
sealed class SecretKeyEventContent : GlobalAccountDataEventContent {
    @Serializable
    data class AesHmacSha2Key(
        @SerialName("name")
        val name: String? = null,
        @SerialName("passphrase")
        @Serializable(with = SecretStorageKeyPassphraseSerializer::class)
        val passphrase: SecretStorageKeyPassphrase? = null,
        @SerialName("iv")
        val iv: String? = null,
        @SerialName("mac")
        val mac: String? = null
    ) : SecretKeyEventContent() {
        @Serializable
        data class AesHmacSha2EncryptedData(
            @SerialName("iv") val iv: String,
            @SerialName("ciphertext") val ciphertext: String,
            @SerialName("mac") val mac: String
        )
    }

    data class Unknown(val raw: JsonObject) : SecretKeyEventContent()

    @OptIn(ExperimentalSerializationApi::class)
    @JsonClassDiscriminator("algorithm")
    @Serializable
    sealed class SecretStorageKeyPassphrase {
        @SerialName("m.pbkdf2")
        data class Pbkdf2(
            @SerialName("salt")
            val salt: String,
            @SerialName("iterations")
            val iterations: Int,
            @SerialName("bits")
            val bits: Int? = 256
        ) : SecretStorageKeyPassphrase()

        data class Unknown(val raw: JsonObject) : SecretStorageKeyPassphrase()
    }
}

object SecretStorageKeyEventContentSerializer : KSerializer<SecretKeyEventContent> {
    override val descriptor = buildClassSerialDescriptor("SecretStorageKeyEventContentSerializer")

    override fun deserialize(decoder: Decoder): SecretKeyEventContent {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement()
        require(jsonObject is JsonObject)
        val algorithm = jsonObject["algorithm"]
        require(algorithm is JsonPrimitive)
        return when (algorithm.content) {
            SecretStorageAlgorithm.AesHmacSha2.value -> decoder.json.decodeFromJsonElement(
                SecretKeyEventContent.AesHmacSha2Key.serializer(),
                jsonObject
            )
            else -> SecretKeyEventContent.Unknown(jsonObject)
        }
    }

    override fun serialize(encoder: Encoder, value: SecretKeyEventContent) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is SecretKeyEventContent.Unknown -> value.raw
            is SecretKeyEventContent.AesHmacSha2Key -> encoder.json.encodeToJsonElement(
                AddFieldsSerializer(
                    SecretKeyEventContent.AesHmacSha2Key.serializer(),
                    "algorithm" to SecretStorageAlgorithm.AesHmacSha2.value
                ), value
            )
        }
        encoder.encodeJsonElement(jsonElement)
    }
}

object AesHmacSha2KeyEventContentSerializer : AddFieldsSerializer<SecretKeyEventContent.AesHmacSha2Key>(
    SecretKeyEventContent.AesHmacSha2Key.serializer(),
    "algorithm" to SecretStorageAlgorithm.AesHmacSha2.value
)

object SecretStorageKeyPassphraseSerializer : KSerializer<SecretKeyEventContent.SecretStorageKeyPassphrase> {
    override val descriptor = buildClassSerialDescriptor("SecretStorageKeyPassphraseSerializer")

    override fun deserialize(decoder: Decoder): SecretKeyEventContent.SecretStorageKeyPassphrase {
        require(decoder is JsonDecoder)
        val jsonElement = decoder.decodeJsonElement()
        require(jsonElement is JsonObject)
        return try {
            decoder.json.decodeFromJsonElement(jsonElement)
        } catch (error: SerializationException) {
            SecretKeyEventContent.SecretStorageKeyPassphrase.Unknown(jsonElement)
        }
    }

    override fun serialize(encoder: Encoder, value: SecretKeyEventContent.SecretStorageKeyPassphrase) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is SecretKeyEventContent.SecretStorageKeyPassphrase.Unknown -> value.raw
            is SecretKeyEventContent.SecretStorageKeyPassphrase.Pbkdf2 -> encoder.json.encodeToJsonElement(value)
        }
        return encoder.encodeJsonElement(jsonElement)
    }
}


