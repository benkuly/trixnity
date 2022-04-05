package net.folivo.trixnity.clientserverapi.model.push

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(with = PusherDataSerializer::class)
data class PusherData(
    val format: String? = null,
    val url: String? = null,
    val customFields: JsonObject? = null
)

object PusherDataSerializer : KSerializer<PusherData> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("PusherDataSerializer")

    override fun deserialize(decoder: Decoder): PusherData {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement()
        if (jsonObject !is JsonObject) throw SerializationException("data must be a JsonObject")
        return PusherData(
            format = jsonObject["format"]?.let { decoder.json.decodeFromJsonElement(it) },
            url = jsonObject["url"]?.let { decoder.json.decodeFromJsonElement(it) },
            customFields = JsonObject(buildMap {
                putAll(jsonObject)
                remove("format")
                remove("url")
            }).takeIf { it.isNotEmpty() }
        )
    }

    override fun serialize(encoder: Encoder, value: PusherData) {
        require(encoder is JsonEncoder)
        encoder.encodeJsonElement(JsonObject(buildMap {
            value.format?.let { put("format", JsonPrimitive(it)) }
            value.url?.let { put("url", JsonPrimitive(it)) }
            value.customFields?.let { putAll(it) }
        }))
    }
}