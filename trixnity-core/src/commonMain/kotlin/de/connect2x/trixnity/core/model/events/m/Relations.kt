package de.connect2x.trixnity.core.model.events.m

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import kotlin.jvm.JvmInline

private val log = KotlinLogging.logger("de.connect2x.trixnity.core.model.events.m.Relations")

@Serializable(with = Relations.Serializer::class)
@JvmInline
value class Relations(val relations: Map<RelationType, ServerAggregation>) {
    object Serializer : KSerializer<Relations> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Relations")

        override fun deserialize(decoder: Decoder): Relations {
            require(decoder is JsonDecoder)
            val aggregationsJson = decoder.decodeJsonElement().jsonObject
            return Relations(
                aggregationsJson
                    .mapKeys { (key, _) -> RelationType.of(key) }
                    .mapValues { (relationType, json) ->
                        try {
                            when (relationType) {
                                is RelationType.Replace -> decoder.json.decodeFromJsonElement<ServerAggregation.Replace>(
                                    json
                                )

                                is RelationType.Thread -> decoder.json.decodeFromJsonElement<ServerAggregation.Thread>(
                                    json
                                )

                                is RelationType.Unknown -> ServerAggregation.Unknown(relationType, json)
                                else -> ServerAggregation.Unknown(relationType, json)
                            }
                        } catch (e: Exception) {
                            log.warn(e) { "malformed relation" }
                            ServerAggregation.Unknown(relationType, json)
                        }
                    }
            )
        }

        override fun serialize(encoder: Encoder, value: Relations) {
            require(encoder is JsonEncoder)
            val aggregationsJson = JsonObject(
                value.relations
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
}

val Relations.replace: ServerAggregation.Replace?
    get() {
        val aggregation = relations[RelationType.Replace]
        return aggregation as? ServerAggregation.Replace
    }

val Relations.thread: ServerAggregation.Thread?
    get() {
        val aggregation = relations[RelationType.Thread]
        return aggregation as? ServerAggregation.Thread
    }

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
        @SerialName("latest_event") val latestEvent: @Contextual RoomEvent<*>,
        @SerialName("count") val count: Long,
        @SerialName("current_user_participated") val currentUserParticipated: Boolean,
    ) : ServerAggregation {
        @Transient
        override val relationType: RelationType.Thread = RelationType.Thread
    }

    data class Unknown(
        override val relationType: RelationType,
        val raw: JsonElement,
    ) : ServerAggregation
}