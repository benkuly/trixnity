package net.folivo.trixnity.core.model.events.m.secretstorage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.core.model.keys.SecretStorageAlgorithm

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#key-storage">matrix spec</a>
 */
@Serializable(with = SecretKeyEventContentSerializer::class)
sealed interface SecretKeyEventContent : GlobalAccountDataEventContent {
    @Serializable
    data class AesHmacSha2Key(
        @SerialName("name")
        val name: String? = null,
        @SerialName("passphrase")
        val passphrase: SecretStorageKeyPassphrase? = null,
        @SerialName("iv")
        val iv: String? = null,
        @SerialName("mac")
        val mac: String? = null
    ) : SecretKeyEventContent {
        @SerialName("algorithm")
        val algorithm = SecretStorageAlgorithm.AesHmacSha2.value

        @Serializable
        data class AesHmacSha2EncryptedData(
            @SerialName("iv") val iv: String,
            @SerialName("ciphertext") val ciphertext: String,
            @SerialName("mac") val mac: String
        )

        @Serializable(with = SecretStorageKeyPassphraseSerializer::class)
        sealed interface SecretStorageKeyPassphrase {
            @Serializable
            data class Pbkdf2(
                @SerialName("salt")
                val salt: String,
                @SerialName("iterations")
                val iterations: Int,
                @SerialName("bits")
                val bits: Int? = 256
            ) : SecretStorageKeyPassphrase {
                @SerialName("algorithm")
                val algorithm: String = "m.pbkdf2"
            }

            data class Unknown(val raw: JsonObject) : SecretStorageKeyPassphrase
        }
    }

    data class Unknown(val raw: JsonObject) : SecretKeyEventContent
}

object SecretKeyEventContentSerializer : KSerializer<SecretKeyEventContent> {
    override val descriptor = buildClassSerialDescriptor("SecretKeyEventContentSerializer")

    override fun deserialize(decoder: Decoder): SecretKeyEventContent {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val algorithm = jsonObject["algorithm"] as? JsonPrimitive
        return when (algorithm?.content) {
            SecretStorageAlgorithm.AesHmacSha2.value -> decoder.json.decodeFromJsonElement<AesHmacSha2Key>(jsonObject)
            else -> SecretKeyEventContent.Unknown(jsonObject)
        }
    }

    override fun serialize(encoder: Encoder, value: SecretKeyEventContent) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is SecretKeyEventContent.Unknown -> value.raw
            is AesHmacSha2Key -> encoder.json.encodeToJsonElement(value)
        }
        encoder.encodeJsonElement(jsonElement)
    }
}

object SecretStorageKeyPassphraseSerializer : KSerializer<AesHmacSha2Key.SecretStorageKeyPassphrase> {
    override val descriptor = buildClassSerialDescriptor("SecretStorageKeyPassphraseSerializer")

    override fun deserialize(decoder: Decoder): AesHmacSha2Key.SecretStorageKeyPassphrase {
        require(decoder is JsonDecoder)
        val jsonElement = decoder.decodeJsonElement().jsonObject
        return try {
            when ((jsonElement["algorithm"] as? JsonPrimitive)?.content) {
                "m.pbkdf2" -> decoder.json.decodeFromJsonElement<AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2>(
                    jsonElement
                )

                else -> AesHmacSha2Key.SecretStorageKeyPassphrase.Unknown(jsonElement)
            }
        } catch (error: Exception) {
            AesHmacSha2Key.SecretStorageKeyPassphrase.Unknown(jsonElement)
        }
    }

    override fun serialize(encoder: Encoder, value: AesHmacSha2Key.SecretStorageKeyPassphrase) {
        require(encoder is JsonEncoder)
        val jsonElement = when (value) {
            is AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2 -> encoder.json.encodeToJsonElement(value)
            is AesHmacSha2Key.SecretStorageKeyPassphrase.Unknown -> value.raw
        }
        return encoder.encodeJsonElement(jsonElement)
    }
}


