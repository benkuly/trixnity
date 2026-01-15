package net.folivo.trixnity.core.model.events.m.secretstorage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.keys.SecretStorageAlgorithm

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#key-storage">matrix spec</a>
 */
@Serializable(with = SecretKeyEventContent.Serializer::class)
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

        @Serializable(with = SecretStorageKeyPassphrase.Serializer::class)
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

            object Serializer : KSerializer<SecretStorageKeyPassphrase> {
                override val descriptor = buildClassSerialDescriptor("SecretStorageKeyPassphrase")

                override fun deserialize(decoder: Decoder): SecretStorageKeyPassphrase {
                    require(decoder is JsonDecoder)
                    val jsonElement = decoder.decodeJsonElement().jsonObject
                    return try {
                        when ((jsonElement["algorithm"] as? JsonPrimitive)?.contentOrNull) {
                            "m.pbkdf2" -> decoder.json.decodeFromJsonElement<Pbkdf2>(jsonElement)
                            else -> Unknown(jsonElement)
                        }
                    } catch (_: Exception) {
                        Unknown(jsonElement)
                    }
                }

                override fun serialize(encoder: Encoder, value: SecretStorageKeyPassphrase) {
                    require(encoder is JsonEncoder)
                    val jsonElement = when (value) {
                        is Pbkdf2 -> encoder.json.encodeToJsonElement(value)
                        is Unknown -> value.raw
                    }
                    return encoder.encodeJsonElement(jsonElement)
                }
            }

        }
    }

    data class Unknown(val raw: JsonObject) : SecretKeyEventContent

    object Serializer : KSerializer<SecretKeyEventContent> {
        override val descriptor = buildClassSerialDescriptor("SecretKeyEventContent")

        override fun deserialize(decoder: Decoder): SecretKeyEventContent {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject
            val algorithm = jsonObject["algorithm"] as? JsonPrimitive
            return when (algorithm?.content) {
                SecretStorageAlgorithm.AesHmacSha2.value -> decoder.json.decodeFromJsonElement<AesHmacSha2Key>(
                    jsonObject
                )

                else -> Unknown(jsonObject)
            }
        }

        override fun serialize(encoder: Encoder, value: SecretKeyEventContent) {
            require(encoder is JsonEncoder)
            val jsonElement = when (value) {
                is Unknown -> value.raw
                is AesHmacSha2Key -> encoder.json.encodeToJsonElement(value)
            }
            encoder.encodeJsonElement(jsonElement)
        }
    }
}

