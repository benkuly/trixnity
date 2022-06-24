package net.folivo.trixnity.core.model.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.jvm.JvmInline

@Serializable(with = AggregationsSerializer::class)
@JvmInline
value class Aggregations(val values: Set<Aggregation>)

object AggregationsSerializer : KSerializer<Aggregations> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AggregationsSerializer")

    override fun deserialize(decoder: Decoder): Aggregations {
        require(decoder is JsonDecoder)
        val aggregationsJson = decoder.decodeJsonElement().jsonObject
        return Aggregations(
            aggregationsJson.map { aggregationJson ->
                when (val name = aggregationJson.key) {
                    else -> Aggregation.UnknownAggregation(name, aggregationJson.value)
                }
            }.toSet()
        )
    }

    override fun serialize(encoder: Encoder, value: Aggregations) {
        require(encoder is JsonEncoder)
        val aggregationsJson = buildJsonObject {
            value.values.forEach {
                when (it) {
                    is Aggregation.UnknownAggregation -> put(it.name, it.raw)
                }
            }
        }
        encoder.encodeJsonElement(aggregationsJson)
    }

}

sealed interface Aggregation {
    data class UnknownAggregation(
        val name: String,
        val raw: JsonElement,
    ) : Aggregation
}
