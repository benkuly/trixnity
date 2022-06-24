package net.folivo.trixnity.core.model.events

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.3/rooms/">matrix spec</a>
 */
sealed interface PersistentDataUnit<C : EventContent> {
    val content: C

    data class UnknownPersistentDataUnit(
        override val content: EmptyEventContent,
        val type: String,
        val raw: JsonObject
    ) : PersistentDataUnit<EmptyEventContent>

    sealed interface PersistentMessageDataUnit<C : MessageEventContent> : PersistentDataUnit<C>

    sealed interface PersistentStateDataUnit<C : StateEventContent> : PersistentDataUnit<C>

    sealed interface PersistentDataUnitV1<C : RoomEventContent> {
        val authEvents: @Serializable(with = EventHashPairListSerializer::class) List<@Contextual EventHashPair>
        val depth: ULong
        val id: EventId
        val hashes: EventHash
        val originTimestamp: Long
        val prevEvents: @Serializable(with = EventHashPairListSerializer::class) List<@Contextual EventHashPair>
        val roomId: RoomId
        val sender: UserId
        val unsigned: UnsignedData?

        @Serializable
        data class PersistentMessageDataUnitV1<C : MessageEventContent>(
            @SerialName("auth_events") override val authEvents: @Serializable(with = EventHashPairListSerializer::class) List<@Contextual EventHashPair>,
            @SerialName("content") override val content: C,
            @SerialName("depth") override val depth: ULong,
            @SerialName("event_id") override val id: EventId,
            @SerialName("hashes") override val hashes: EventHash,
            @SerialName("origin_server_ts") override val originTimestamp: Long,
            @SerialName("prev_events") override val prevEvents: @Serializable(with = EventHashPairListSerializer::class) List<@Contextual EventHashPair>,
            @SerialName("room_id") override val roomId: RoomId,
            @SerialName("sender") override val sender: UserId,
            @SerialName("unsigned") override val unsigned: UnsignedData? = null
        ) : PersistentMessageDataUnit<C>, PersistentDataUnitV1<C>

        @Serializable
        data class PersistentStateDataUnitV1<C : StateEventContent>(
            @SerialName("auth_events") override val authEvents: @Serializable(with = EventHashPairListSerializer::class) List<@Contextual EventHashPair>,
            @SerialName("content") override val content: C,
            @SerialName("depth") override val depth: ULong,
            @SerialName("event_id") override val id: EventId,
            @SerialName("hashes") override val hashes: EventHash,
            @SerialName("origin_server_ts") override val originTimestamp: Long,
            @SerialName("prev_events") override val prevEvents: @Serializable(with = EventHashPairListSerializer::class) List<@Contextual EventHashPair>,
            @SerialName("room_id") override val roomId: RoomId,
            @SerialName("sender") override val sender: UserId,
            @SerialName("state_key") val stateKey: String,
            @SerialName("unsigned") override val unsigned: UnsignedData? = null
        ) : PersistentStateDataUnit<C>, PersistentDataUnitV1<C>

        data class EventHashPair(
            val eventId: EventId,
            val hash: EventHash?
        )

        object EventHashPairListSerializer : KSerializer<List<EventHashPair>> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EventHashPairListSerializer")

            override fun deserialize(decoder: Decoder): List<EventHashPair> {
                require(decoder is JsonDecoder)
                val jsonArray = decoder.decodeJsonElement()
                if (jsonArray !is JsonArray) throw SerializationException("authEvents has to be json array")
                return jsonArray.chunked(2).map {
                    val eventIdValue = it.getOrNull(0)
                    if (eventIdValue !is JsonPrimitive) throw SerializationException("authEvents should be pair of event id and hash")
                    EventHashPair(
                        eventId = EventId(eventIdValue.content),
                        hash = it.getOrNull(1)?.let { value -> decoder.json.decodeFromJsonElement(value) }
                    )
                }
            }

            override fun serialize(encoder: Encoder, value: List<EventHashPair>) {
                require(encoder is JsonEncoder)
                encoder.encodeJsonElement(buildJsonArray {
                    value.forEach {
                        add(JsonPrimitive(it.eventId.full))
                        add(encoder.json.encodeToJsonElement(it.hash))
                    }
                })
            }
        }
    }

    sealed interface PersistentDataUnitV3<C : RoomEventContent> {
        val authEvents: List<EventId>
        val depth: ULong
        val hashes: EventHash
        val originTimestamp: Long
        val prevEvents: List<EventId>
        val roomId: RoomId
        val sender: UserId
        val unsigned: UnsignedData?

        @Serializable
        data class PersistentMessageDataUnitV3<C : MessageEventContent>(
            @SerialName("auth_events") override val authEvents: List<EventId>,
            @SerialName("content") override val content: C,
            @SerialName("depth") override val depth: ULong,
            @SerialName("hashes") override val hashes: EventHash,
            @SerialName("origin_server_ts") override val originTimestamp: Long,
            @SerialName("prev_events") override val prevEvents: List<EventId>,
            @SerialName("room_id") override val roomId: RoomId,
            @SerialName("sender") override val sender: UserId,
            @SerialName("unsigned") override val unsigned: UnsignedData? = null
        ) : PersistentMessageDataUnit<C>, PersistentDataUnitV3<C>

        @Serializable
        data class PersistentStateDataUnitV3<C : StateEventContent>(
            @SerialName("auth_events") override val authEvents: List<EventId>,
            @SerialName("content") override val content: C,
            @SerialName("depth") override val depth: ULong,
            @SerialName("hashes") override val hashes: EventHash,
            @SerialName("origin_server_ts") override val originTimestamp: Long,
            @SerialName("prev_events") override val prevEvents: List<EventId>,
            @SerialName("room_id") override val roomId: RoomId,
            @SerialName("sender") override val sender: UserId,
            @SerialName("state_key") val stateKey: String,
            @SerialName("unsigned") override val unsigned: UnsignedData? = null
        ) : PersistentStateDataUnit<C>, PersistentDataUnitV3<C>
    }

    @Serializable
    data class EventHash(
        @SerialName("sha256") val sha256: String
    )

    @Serializable
    data class UnsignedData(
        @SerialName("age") val age: Long? = null
    )
}