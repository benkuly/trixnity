package net.folivo.trixnity.clientserverapi.model.devices

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.ExperimentalTrixnityApi

@Serializable(with = DehydratedDeviceDataSerializer::class)
sealed interface DehydratedDeviceData {
    val algorithm: String

    @Serializable
    data class DehydrationV2(
        @SerialName("device_pickle")
        val devicePickle: String,
        @SerialName("nonce")
        val nonce: String,
    ) : DehydratedDeviceData {
        @SerialName("algorithm")
        override val algorithm: String = "m.dehydration.v2"
    }

    data class Unknown(override val algorithm: String, val raw: JsonObject) : DehydratedDeviceData
}

@OptIn(ExperimentalTrixnityApi::class)
private class DehydratedDeviceDataSerializer : KSerializer<DehydratedDeviceData> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DehydratedDeviceData")
    override fun deserialize(decoder: Decoder): DehydratedDeviceData {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        val algorithm = jsonObject["algorithm"]?.jsonPrimitive?.content
        return when (algorithm) {
            "m.dehydration.v2" -> decoder.json.decodeFromJsonElement<DehydratedDeviceData.DehydrationV2>(jsonObject)
            else -> DehydratedDeviceData.Unknown(algorithm ?: "unknown", jsonObject)
        }
    }

    override fun serialize(encoder: Encoder, value: DehydratedDeviceData) {
        require(encoder is JsonEncoder)
        when (value) {
            is DehydratedDeviceData.DehydrationV2 -> encoder.encodeJsonElement(encoder.json.encodeToJsonElement(value))
            is DehydratedDeviceData.Unknown -> encoder.encodeJsonElement(JsonObject(buildMap {
                put("algorithm", JsonPrimitive(value.algorithm))
                putAll(value.raw)
            }))
        }
    }
}