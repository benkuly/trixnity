package net.folivo.trixnity.clientserverapi.model.sync

import io.ktor.resources.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.overwriteWith
import kotlinx.serialization.serializer
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.EphemeralEvent
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StrippedStateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.m.Presence
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EphemeralEventSerializer
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.MessageEventSerializer
import net.folivo.trixnity.core.serialization.events.RoomAccountDataEventSerializer
import net.folivo.trixnity.core.serialization.events.RoomEventSerializer
import net.folivo.trixnity.core.serialization.events.StateBaseEventSerializer
import net.folivo.trixnity.core.serialization.events.StateEventSerializer
import net.folivo.trixnity.core.serialization.events.StrippedStateEventSerializer

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3sync">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/sync")
@HttpMethod(GET)
data class Sync(
    @SerialName("filter") val filter: String? = null,
    @SerialName("full_state") val fullState: Boolean? = null,
    @SerialName("set_presence") val setPresence: Presence? = null,
    @SerialName("since") val since: String? = null,
    @SerialName("timeout") val timeout: Long? = null,
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Unit, Sync.Response> {
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: Response?
    ): KSerializer<Response> = Response.serializer()

    @Serializable
    data class Response(
        @SerialName("next_batch") val nextBatch: String,
        @SerialName("rooms") val room: Rooms? = null,
        @SerialName("presence") val presence: Presence? = null,
        @SerialName("account_data") val accountData: GlobalAccountData? = null,
        @SerialName("to_device") val toDevice: ToDevice? = null,
        @SerialName("device_lists") val deviceLists: DeviceLists? = null,
        @SerialName("device_one_time_keys_count") val oneTimeKeysCount: OneTimeKeysCount? = null,
        @SerialName("device_unused_fallback_key_types") val unusedFallbackKeyTypes: UnusedFallbackKeyTypes? = null,
    ) {
        abstract class RoomsMapSerializer<T>(
            valueDescriptor: SerialDescriptor,
            private val valueSerializer: (Json) -> KSerializer<T>,
        ) : KSerializer<Map<RoomId, T>> {
            private val keySerializer = RoomId.serializer()

            @OptIn(ExperimentalSerializationApi::class)
            override val descriptor: SerialDescriptor = mapSerialDescriptor(
                keySerializer.descriptor,
                valueDescriptor,
            )

            override fun serialize(
                encoder: Encoder,
                value: Map<RoomId, T>,
            ) {
                require(encoder is JsonEncoder)
                val mapSerializer = MapSerializer(keySerializer, valueSerializer(encoder.json))
                encoder.encodeSerializableValue(mapSerializer, value)
            }

            @OptIn(ExperimentalSerializationApi::class)
            override fun deserialize(decoder: Decoder): Map<RoomId, T> {
                return decoder.decodeStructure(descriptor) {
                    buildMap {
                        var key: RoomId? = null
                        loop@ while (true) {
                            val index = decodeElementIndex(descriptor)
                            // We've got keys and values interleaved,
                            // with keys in even positions and values in odd positions
                            val isKey = index % 2 == 0
                            when {
                                index == DECODE_DONE -> break@loop
                                isKey -> {
                                    key = decodeSerializableElement(descriptor, index, keySerializer)
                                }
                                else -> {
                                    requireNotNull(key)
                                    require(decoder is JsonDecoder)
                                    val json = Json(decoder.json) {
                                        serializersModule = decoder.serializersModule
                                            .overwriteWith(buildSerializersModule(key))
                                    }
                                    put(key, decodeSerializableElement(descriptor, index, valueSerializer(json)))
                                }
                            }
                        }
                    }
                }
            }
        }

        private object RoomsKnockSerializer: RoomsMapSerializer<Rooms.KnockedRoom>(
            Rooms.KnockedRoom.serializer().descriptor,
            { ContextualSerializer(it, it.serializersModule.serializer()) },
        )

        private object RoomsJoinSerializer: RoomsMapSerializer<Rooms.JoinedRoom>(
            Rooms.JoinedRoom.serializer().descriptor,
            { ContextualSerializer(it, it.serializersModule.serializer()) },
        )

        private object RoomsInviteSerializer: RoomsMapSerializer<Rooms.InvitedRoom>(
            Rooms.InvitedRoom.serializer().descriptor,
            { ContextualSerializer(it, it.serializersModule.serializer()) },
        )

        private object RoomsLeaveSerializer: RoomsMapSerializer<Rooms.LeftRoom>(
            Rooms.LeftRoom.serializer().descriptor,
            { ContextualSerializer(it, it.serializersModule.serializer()) },
        )

        private class ContextualSerializer<T>(val json: Json, val serializer: KSerializer<T>): KSerializer<T> {
            override val descriptor: SerialDescriptor get() = serializer.descriptor

            override fun serialize(encoder: Encoder, value: T) {
                require(encoder is JsonEncoder)
                encoder.encodeJsonElement(json.encodeToJsonElement(serializer, value))
            }

            override fun deserialize(decoder: Decoder): T {
                require(decoder is JsonDecoder)
                return json.decodeFromJsonElement(serializer, decoder.decodeJsonElement())
            }
        }

        @Serializable
        data class Rooms(
            @SerialName("knock") val knock: @Serializable(with = RoomsKnockSerializer::class) Map<RoomId, KnockedRoom>? = null,
            @SerialName("join") val join: @Serializable(with = RoomsJoinSerializer::class) Map<RoomId, JoinedRoom>? = null,
            @SerialName("invite") val invite: @Serializable(with = RoomsInviteSerializer::class) Map<RoomId, InvitedRoom>? = null,
            @SerialName("leave") val leave: @Serializable(with = RoomsLeaveSerializer::class) Map<RoomId, LeftRoom>? = null
        ) {
            @Serializable
            data class KnockedRoom(
                @SerialName("knock_state") val knockState: InviteState? = null
            ) {
                @Serializable
                data class InviteState(
                    @SerialName("events") val events: List<@Contextual StrippedStateEvent<*>>? = null
                )
            }

            @Serializable
            data class JoinedRoom(
                @SerialName("summary") val summary: RoomSummary? = null,
                @SerialName("state") val state: State? = null,
                @SerialName("timeline") val timeline: Timeline? = null,
                @SerialName("ephemeral") val ephemeral: Ephemeral? = null,
                @SerialName("account_data") val accountData: RoomAccountData? = null,
                @SerialName("unread_notifications") val unreadNotifications: UnreadNotificationCounts? = null,
                @SerialName("unread_thread_notifications") val unreadThreadNotifications: Map<EventId, UnreadThreadNotificationCounts>? = null
            ) {
                @Serializable
                data class RoomSummary(
                    @SerialName("m.heroes") val heroes: List<UserId>? = null,
                    @SerialName("m.joined_member_count") val joinedMemberCount: Long? = null,
                    @SerialName("m.invited_member_count") val invitedMemberCount: Long? = null
                )

                @Serializable
                data class Ephemeral(
                    @SerialName("events") val events: List<@Contextual EphemeralEvent<*>>? = null
                )

                @Serializable
                data class UnreadNotificationCounts(
                    @SerialName("highlight_count") val highlightCount: Long? = null,
                    @SerialName("notification_count") val notificationCount: Long? = null
                )

                @Serializable
                data class UnreadThreadNotificationCounts(
                    @SerialName("highlight_count") val highlightCount: Long? = null,
                    @SerialName("notification_count") val notificationCount: Long? = null
                )
            }

            @Serializable
            data class InvitedRoom(
                @SerialName("invite_state") val inviteState: InviteState? = null
            ) {
                @Serializable
                data class InviteState(
                    @SerialName("events") val events: List<@Contextual StrippedStateEvent<*>>? = null
                )
            }

            @Serializable
            data class LeftRoom(
                @SerialName("state") val state: State? = null,
                @SerialName("timeline") val timeline: Timeline? = null,
                @SerialName("account_data") val accountData: RoomAccountData? = null
            )

            @Serializable
            data class State(
                @SerialName("events") val events: List<@Contextual StateEvent<*>>? = null
            )

            @Serializable
            data class Timeline(
                @SerialName("events") val events: List<@Contextual RoomEvent<*>>? = null,
                @SerialName("limited") val limited: Boolean? = null,
                @SerialName("prev_batch") val previousBatch: String? = null
            )

            @Serializable
            data class RoomAccountData(
                @SerialName("events") val events: List<@Contextual RoomAccountDataEvent<*>>? = null
            )
        }

        @Serializable
        data class Presence(
            @SerialName("events") val events: List<@Contextual EphemeralEvent<PresenceEventContent>>? = null
        )

        @Serializable
        data class GlobalAccountData(
            @SerialName("events") val events: List<@Contextual GlobalAccountDataEvent<*>>? = null
        )

        @Serializable
        data class DeviceLists(
            @SerialName("changed") val changed: Set<UserId>? = null,
            @SerialName("left") val left: Set<UserId>? = null
        )

        @Serializable
        data class ToDevice(
            @SerialName("events") val events: List<@Contextual ToDeviceEvent<*>>? = null
        )
    }

    companion object {
        internal fun addRoomIdToEvent(event: JsonObject, roomId: RoomId): JsonObject {
            return JsonObject(buildMap {
                putAll(event)
                put("room_id", JsonPrimitive(roomId.full))
                val unsigned = event["unsigned"] as? JsonObject
                if (unsigned != null) {
                    val aggregations = unsigned["m.relations"] as? JsonObject
                    val newAggregations =
                        if (aggregations != null) {
                            val thread = aggregations["m.thread"] as? JsonObject
                            if (thread != null) {
                                val latestEvent = thread["latest_event"] as? JsonObject
                                if (latestEvent != null) {
                                    JsonObject(buildMap {
                                        putAll(aggregations)
                                        put("m.thread", JsonObject(buildMap {
                                            putAll(thread)
                                            put("latest_event", addRoomIdToEvent(latestEvent, roomId))
                                        }))
                                    })
                                } else null
                            } else null
                        } else null
                    val redactedBecause = unsigned["redacted_because"] as? JsonObject
                    val newRedactedBecause =
                        if (redactedBecause != null) {
                            addRoomIdToEvent(redactedBecause, roomId)
                        } else null
                    put("unsigned", JsonObject(buildMap {
                        putAll(unsigned)
                        if (newAggregations != null) {
                            put("m.relations", newAggregations)
                        }
                        if (newRedactedBecause != null) {
                            put("redacted_because", newRedactedBecause)
                        }
                    }))
                }
            })
        }


        private fun buildSerializersModule(roomId: RoomId): SerializersModule {
            val mappings = DefaultEventContentSerializerMappings
            val messageEventSerializer = WithRoomIdSerializer(roomId, MessageEventSerializer(mappings.message))
            val stateEventSerializer = WithRoomIdSerializer(roomId, StateEventSerializer(mappings.state))
            val roomEventSerializer = WithRoomIdSerializer(roomId, RoomEventSerializer(messageEventSerializer, stateEventSerializer))
            val strippedStateEventSerializer = WithRoomIdSerializer(roomId, StrippedStateEventSerializer(mappings.state))
            val stateBaseEventSerializer = WithRoomIdSerializer(roomId, StateBaseEventSerializer(stateEventSerializer, strippedStateEventSerializer))
            val ephemeralEventSerializer = WithRoomIdSerializer(roomId, EphemeralEventSerializer(mappings.ephemeral))
            val roomAccountDataEventSerializer = WithRoomIdSerializer(roomId, RoomAccountDataEventSerializer(mappings.roomAccountData))
            return SerializersModule {
                contextual(roomEventSerializer)
                contextual(messageEventSerializer)
                contextual(stateEventSerializer)
                contextual(strippedStateEventSerializer)
                contextual(stateBaseEventSerializer)
                contextual(ephemeralEventSerializer)
                contextual(roomAccountDataEventSerializer)
            }
        }
    }

    private class WithRoomIdSerializer<T>(private val roomId: RoomId, serializer: KSerializer<T>)
        : JsonTransformingSerializer<T>(serializer) {
        override fun transformDeserialize(element: JsonElement): JsonElement {
            require(element is JsonObject)
            return addRoomIdToEvent(element, roomId)
        }
    }
}

typealias OneTimeKeysCount = Map<KeyAlgorithm, Int>
typealias UnusedFallbackKeyTypes = Set<KeyAlgorithm>