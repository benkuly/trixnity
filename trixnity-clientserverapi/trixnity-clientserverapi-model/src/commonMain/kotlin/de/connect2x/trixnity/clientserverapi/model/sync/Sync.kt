package de.connect2x.trixnity.clientserverapi.model.sync

import io.ktor.resources.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.overwriteWith
import de.connect2x.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.RoomMap
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.*
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.PresenceEventContent
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
import de.connect2x.trixnity.core.serialization.events.*
import kotlin.jvm.JvmInline

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3sync">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/sync")
@HttpMethod(GET)
data class Sync(
    @SerialName("filter") val filter: String? = null,
    @SerialName("full_state") val fullState: Boolean? = null,
    @SerialName("use_state_after") val useStateAfter: Boolean? = null,
    @SerialName("set_presence") val setPresence: Presence? = null,
    @SerialName("since") val since: String? = null,
    @SerialName("timeout") val timeout: Long? = null,
) : MatrixEndpoint<Unit, Sync.Response> {
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: Response?
    ): KSerializer<Response> = SyncResponseSerializer(json, mappings)

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

        @Serializable
        data class Rooms(
            @SerialName("invite") val invite: @Contextual RoomMap<InvitedRoom>? = null,
            @SerialName("join") val join: @Contextual RoomMap<JoinedRoom>? = null,
            @SerialName("knock") val knock: @Contextual RoomMap<KnockedRoom>? = null,
            @SerialName("leave") val leave: @Contextual RoomMap<LeftRoom>? = null
        ) {
            @JvmInline
            value class RoomMap<T>(private val delegate: Map<RoomId, T>) : Map<RoomId, T> by delegate {
                companion object {
                    fun <T> roomMapOf(vararg pairs: Pair<RoomId, T>) = RoomMap(mapOf(*pairs))
                }
            }

            @Serializable
            data class KnockedRoom(
                @SerialName("knock_state") val strippedState: StrippedState? = null
            )

            @Serializable
            data class JoinedRoom(
                @SerialName("summary") val summary: RoomSummary? = null,
                @SerialName("state") val state: State? = null,
                @SerialName("timeline") val timeline: Timeline? = null,
                @SerialName("state_after") val stateAfter: State? = null,
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
                @SerialName("invite_state") val strippedState: StrippedState? = null
            )

            @Serializable
            data class LeftRoom(
                @SerialName("state") val state: State? = null,
                @SerialName("timeline") val timeline: Timeline? = null,
                @SerialName("state_after") val stateAfter: State? = null,
                @SerialName("account_data") val accountData: RoomAccountData? = null
            )

            @Serializable
            data class State(
                @SerialName("events") val events: List<@Contextual StateEvent<*>>? = null
            )

            @Serializable
            data class StrippedState(
                @SerialName("events") val events: List<@Contextual StrippedStateEvent<*>>? = null
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
}

typealias OneTimeKeysCount = Map<KeyAlgorithm, Int>
typealias UnusedFallbackKeyTypes = Set<KeyAlgorithm>

fun Sync.Response.allEvents(): List<ClientEvent<*>> = buildList {
    toDevice?.events?.forEach { add(it) }
    accountData?.events?.forEach { add(it) }
    presence?.events?.forEach { add(it) }
    room?.join?.forEach { (_, joinedRoom) ->
        joinedRoom.state?.events?.forEach { add(it) }
        joinedRoom.timeline?.events?.forEach { add(it) }
        joinedRoom.stateAfter?.events?.forEach { add(it) }
        joinedRoom.ephemeral?.events?.forEach { add(it) }
        joinedRoom.accountData?.events?.forEach { add(it) }
    }
    room?.invite?.forEach { (_, invitedRoom) ->
        invitedRoom.strippedState?.events?.forEach { add(it) }
    }
    room?.knock?.forEach { (_, invitedRoom) ->
        invitedRoom.strippedState?.events?.forEach { add(it) }
    }
    room?.leave?.forEach { (_, leftRoom) ->
        leftRoom.state?.events?.forEach { add(it) }
        leftRoom.timeline?.events?.forEach { add(it) }
        leftRoom.stateAfter?.events?.forEach { add(it) }
        leftRoom.accountData?.events?.forEach { add(it) }
    }
}

class SyncResponseSerializer(
    json: Json,
    mappings: EventContentSerializerMappings,
) : ContextualSerializer<Sync.Response>(
    json = Json(json) {
        serializersModule = json.serializersModule.overwriteWith(buildRoomMapSerializerModule(mappings))
    },
    serializer = Sync.Response.serializer()
)

private class RoomsMapSerializer<T>(
    valueDescriptor: SerialDescriptor,
    private val valueSerializer: (Json) -> KSerializer<T>,
    private val mappings: EventContentSerializerMappings,
) : KSerializer<RoomMap<T>> {
    private val keySerializer = RoomId.serializer()

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = mapSerialDescriptor(
        keySerializer.descriptor,
        valueDescriptor,
    )

    override fun serialize(
        encoder: Encoder,
        value: RoomMap<T>,
    ) {
        require(encoder is JsonEncoder)
        val mapSerializer = MapSerializer(keySerializer, valueSerializer(encoder.json))
        encoder.encodeSerializableValue(mapSerializer, value)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): RoomMap<T> {
        return decoder.decodeStructure(descriptor) {
            RoomMap(
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
                                        .overwriteWith(buildInjectRoomIdSerializersModule(key, mappings))
                                }
                                put(
                                    key,
                                    decodeSerializableElement(descriptor, index, valueSerializer(json))
                                )
                            }
                        }
                    }
                })
        }
    }
}

open class ContextualSerializer<T>(val json: Json, val serializer: KSerializer<T>) : KSerializer<T> {
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

private class WithRoomIdSerializer<T>(private val roomId: RoomId, serializer: KSerializer<T>) :
    JsonTransformingSerializer<T>(serializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        require(element is JsonObject)
        return addRoomIdToEvent(element, roomId)
    }
}

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

private fun buildRoomMapSerializerModule(
    mappings: EventContentSerializerMappings,
): SerializersModule {
    return SerializersModule {
        contextual(RoomMap::class) { args ->
            val serializer = args[0]
            RoomsMapSerializer(
                serializer.descriptor,
                { ContextualSerializer(it, serializer) },
                mappings,
            )
        }
    }
}


private fun buildInjectRoomIdSerializersModule(
    roomId: RoomId,
    mappings: EventContentSerializerMappings
): SerializersModule {
    val messageEventSerializer =
        WithRoomIdSerializer(roomId, MessageEventSerializer(mappings.message))
    val stateEventSerializer =
        WithRoomIdSerializer(roomId, StateEventSerializer(mappings.state))
    val roomEventSerializer =
        WithRoomIdSerializer(roomId, RoomEventSerializer(messageEventSerializer, stateEventSerializer))
    val strippedStateEventSerializer =
        WithRoomIdSerializer(roomId, StrippedStateEventSerializer(mappings.state))
    val stateBaseEventSerializer =
        WithRoomIdSerializer(roomId, StateBaseEventSerializer(stateEventSerializer, strippedStateEventSerializer))
    val ephemeralEventSerializer =
        WithRoomIdSerializer(roomId, EphemeralEventSerializer(mappings.ephemeral))
    val roomAccountDataEventSerializer =
        WithRoomIdSerializer(roomId, RoomAccountDataEventSerializer(mappings.roomAccountData))
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