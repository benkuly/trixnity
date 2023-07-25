package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event

@Serializable(with = AggregationsSerializer::class) // this is no typealias because it fails on native targets
class Relations(private val delegate: Map<RelationType, ServerAggregation>) :
    Map<RelationType, ServerAggregation> by delegate {
    override fun hashCode(): Int = delegate.hashCode()
    override fun equals(other: Any?): Boolean = delegate == other
}

val Map<RelationType, ServerAggregation>.replace: ServerAggregation.Replace?
    get() {
        val aggregation = this[RelationType.Replace]
        return if (aggregation is ServerAggregation.Replace) aggregation
        else null
    }

val Map<RelationType, ServerAggregation>.thread: ServerAggregation.Thread?
    get() {
        val aggregation = this[RelationType.Thread]
        return if (aggregation is ServerAggregation.Thread) aggregation
        else null
    }

operator fun Map<RelationType, ServerAggregation>.plus(other: ServerAggregation?): Relations =
    if (other == null) Relations(this) else Relations(plus(other.relationType to other))

operator fun Map<RelationType, ServerAggregation>.minus(other: ServerAggregation?): Relations =
    if (other == null) Relations(this) else Relations(minus(other.relationType))

sealed interface ServerAggregation {
    val relationType: RelationType

    // TODO since matrix 1.7 this is a full RoomEvent. We keep this unchanged for now to be backwards compatible.
    @Serializable
    data class Replace(
        @SerialName("event_id") val eventId: EventId,
        @SerialName("sender") val sender: UserId,
        @SerialName("origin_server_ts") val originTimestamp: Long,
    ) : ServerAggregation {
        @Transient
        override val relationType: RelationType.Replace = RelationType.Replace
    }

    @Serializable
    data class Thread(
        @SerialName("latest_event") val latestEvent: @Contextual Event<*>,
        @SerialName("count") val count: Long,
        @SerialName("current_user_participated") val currentUserParticipated: Boolean,
    ) : ServerAggregation {
        @Transient
        override val relationType: RelationType.Thread = RelationType.Thread
    }

    data class Unknown(
        override val relationType: RelationType.Unknown,
        val raw: JsonElement,
    ) : ServerAggregation
}

object AggregationsSerializer : KSerializer<Relations> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AggregationsSerializer")

    override fun deserialize(decoder: Decoder): Relations {
        require(decoder is JsonDecoder)
        val aggregationsJson = decoder.decodeJsonElement().jsonObject
        return Relations(aggregationsJson
            .mapKeys { (key, _) -> RelationType.of(key) }
            .mapValues { (relationType, json) ->
                when (relationType) {
                    is RelationType.Replace -> decoder.json.decodeFromJsonElement<ServerAggregation.Replace>(json)
                    is RelationType.Thread -> decoder.json.decodeFromJsonElement<ServerAggregation.Thread>(json)
                    is RelationType.Unknown -> ServerAggregation.Unknown(relationType, json)
                    else -> ServerAggregation.Unknown(RelationType.Unknown(relationType.name), json)
                }
            })
    }

    override fun serialize(encoder: Encoder, value: Relations) {
        require(encoder is JsonEncoder)
        val aggregationsJson = JsonObject(
            value
                .mapKeys { (_, value) -> value.relationType.name }
                .mapValues { (_, value) ->
                    when (value) {
                        is ServerAggregation.Replace -> encoder.json.encodeToJsonElement(value)
                        is ServerAggregation.Thread -> encoder.json.encodeToJsonElement(value)
                        is ServerAggregation.Unknown -> value.raw
                    }
                }
        )
        encoder.encodeJsonElement(aggregationsJson)
    }

}