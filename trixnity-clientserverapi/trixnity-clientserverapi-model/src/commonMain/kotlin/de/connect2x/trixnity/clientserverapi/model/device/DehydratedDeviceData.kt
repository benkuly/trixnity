package de.connect2x.trixnity.clientserverapi.model.device

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import de.connect2x.trixnity.core.MSC3814

@Serializable(with = DehydratedDeviceData.Serializer::class)
@OptIn(MSC3814::class)
sealed interface DehydratedDeviceData {
    val algorithm: String

    @Serializable
    data class DehydrationV2(
        @SerialName("device_pickle")
        val devicePickle: String,
        @SerialName("nonce")
        val nonce: String,
    ) : DehydratedDeviceData {
        companion object {
            const val ALGORITHM = "org.matrix.msc3814.v2"
        }

        @SerialName("algorithm")
        override val algorithm: String = ALGORITHM
    }

    @Serializable
    data class DehydrationV2Compatibility(
        @SerialName("iv")
        val iv: String,
        @SerialName("encrypted_device_pickle")
        val encryptedDevicePickle: String,
        @SerialName("mac")
        val mac: String
    ) : DehydratedDeviceData {
        companion object {
            const val ALGORITHM = "trixnity.msc3814.compatibility"
        }

        @SerialName("algorithm")
        override val algorithm: String = ALGORITHM
    }

    data class Unknown(override val algorithm: String, val raw: JsonObject) : DehydratedDeviceData

    class Serializer : KSerializer<DehydratedDeviceData> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DehydratedDeviceData")
        override fun deserialize(decoder: Decoder): DehydratedDeviceData {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject
            val algorithm = jsonObject["algorithm"]?.jsonPrimitive?.content
            return when (algorithm) {
                DehydrationV2.ALGORITHM -> decoder.json.decodeFromJsonElement<DehydrationV2>(jsonObject)
                DehydrationV2Compatibility.ALGORITHM -> decoder.json.decodeFromJsonElement<DehydrationV2Compatibility>(
                    jsonObject
                )

                else -> Unknown(algorithm ?: "unknown", jsonObject)
            }
        }

        override fun serialize(encoder: Encoder, value: DehydratedDeviceData) {
            require(encoder is JsonEncoder)
            when (value) {
                is DehydrationV2 -> encoder.encodeJsonElement(encoder.json.encodeToJsonElement(value))
                is DehydrationV2Compatibility -> encoder.encodeJsonElement(encoder.json.encodeToJsonElement(value))

                is Unknown -> encoder.encodeJsonElement(JsonObject(buildMap {
                    put("algorithm", JsonPrimitive(value.algorithm))
                    putAll(value.raw)
                }))
            }
        }
    }
}