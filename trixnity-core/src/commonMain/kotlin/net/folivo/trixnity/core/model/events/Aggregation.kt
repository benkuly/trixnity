package net.folivo.trixnity.core.model.events

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId

@Serializable(with = AggregationsSerializer::class) // this is no typealias because it fails on native targets
class Aggregations(private val delegate: Map<RelationType, Aggregation>) : Map<RelationType, Aggregation> by delegate {
    override fun hashCode(): Int = delegate.hashCode()
    override fun equals(other: Any?): Boolean = delegate == other
}

val Map<RelationType, Aggregation>.replace: Aggregation.Replace?
    get() {
        val aggregation = this[RelationType.Replace]
        return if (aggregation is Aggregation.Replace) aggregation
        else null
    }

val Map<RelationType, Aggregation>.thread: Aggregation.Thread?
    get() {
        val aggregation = this[RelationType.Thread]
        return if (aggregation is Aggregation.Thread) aggregation
        else null
    }

operator fun Map<RelationType, Aggregation>.plus(other: Aggregation?): Aggregations =
    if (other == null) Aggregations(this) else Aggregations(plus(other.relationType to other))

operator fun Map<RelationType, Aggregation>.minus(other: Aggregation?): Aggregations =
    if (other == null) Aggregations(this) else Aggregations(minus(other.relationType))

sealed interface Aggregation {
    val relationType: RelationType

    // TODO since matrix 1.7 this is a full RoomEvent. We keep this unchanged for now to be backwards compatible.
    @Serializable
    data class Replace(
        @SerialName("event_id") val eventId: EventId,
        @SerialName("sender") val sender: UserId,
        @SerialName("origin_server_ts") val originTimestamp: Long,
    ) : Aggregation {
        @Transient
        override val relationType: RelationType.Replace = RelationType.Replace
    }

    @Serializable
    data class Thread(
        @SerialName("latest_event") val latestEvent: @Contextual Event<*>,
        @SerialName("count") val count: Long,
        @SerialName("current_user_participated") val currentUserParticipated: Boolean,
    ) : Aggregation {
        @Transient
        override val relationType: RelationType.Thread = RelationType.Thread
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
            .mapValues { (relationType, json) ->
                when (relationType) {
                    is RelationType.Replace -> decoder.json.decodeFromJsonElement<Aggregation.Replace>(json)
                    is RelationType.Thread -> decoder.json.decodeFromJsonElement<Aggregation.Thread>(json)
                    is RelationType.Unknown -> Aggregation.Unknown(relationType, json)
                    else -> Aggregation.Unknown(RelationType.Unknown(relationType.name), json)
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
                        is Aggregation.Thread -> encoder.json.encodeToJsonElement(value)
                        is Aggregation.Unknown -> value.raw
                    }
                }
        )
        encoder.encodeJsonElement(aggregationsJson)
    }

}