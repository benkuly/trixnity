package net.folivo.trixnity.core.model.events

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId

@Serializable(with = AggregationsSerializer::class)
class Aggregations(delegate: Map<RelationType, Aggregation>) : Map<RelationType, Aggregation> by delegate

val Aggregations.replace: Aggregation.Replace?
    get() {
        val aggregation = this[RelationType.Replace]
        return if (aggregation is Aggregation.Replace) aggregation
        else null
    }

operator fun Aggregations.plus(other: Aggregation?): Aggregations =
    if (other == null) this else Aggregations(plus(other.relationType to other))

operator fun Aggregations.minus(other: Aggregation?): Aggregations =
    if (other == null) this else Aggregations(minus(other.relationType))

sealed interface Aggregation {
    val relationType: RelationType

    @Serializable
    data class Replace(
        @SerialName("event_id") val eventId: EventId,
        @SerialName("sender") val sender: UserId,
        @SerialName("origin_server_ts") val originTimestamp: Long,
    ) : Aggregation {
        @Transient
        override val relationType: RelationType.Replace = RelationType.Replace
    }

    data class Unknown(
        override val relationType: RelationType.Unknown,
        val raw: JsonElement,
    ) : Aggregation
}

object AggregationsSerializer : KSerializer<Aggregations> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AggregationsSerializer")

    override fun deserialize(decoder: Decoder): Aggregations {
        require(decoder is JsonDecoder)
        val aggregationsJson = decoder.decodeJsonElement().jsonObject
        return Aggregations(aggregationsJson
            .mapKeys { (key, _) -> RelationType.of(key) }
            .mapValues { (relationType, aggregationJson) ->
                when (relationType) {
                    is RelationType.Replace -> decoder.json.decodeFromJsonElement<Aggregation.Replace>(
                        aggregationJson
                    )

                    is RelationType.Unknown -> Aggregation.Unknown(relationType, aggregationJson)
                    else -> Aggregation.Unknown(RelationType.Unknown(relationType.name), aggregationJson)
                }
            })
    }

    override fun serialize(encoder: Encoder, value: Aggregations) {
        require(encoder is JsonEncoder)
        val aggregationsJson = JsonObject(
            value
                .mapKeys { (_, value) -> value.relationType.name }
                .mapValues { (_, value) ->
                    when (value) {
                        is Aggregation.Replace -> encoder.json.encodeToJsonElement(value)
                        is Aggregation.Unknown -> value.raw
                    }
                }
        )
        encoder.encodeJsonElement(aggregationsJson)
    }

}