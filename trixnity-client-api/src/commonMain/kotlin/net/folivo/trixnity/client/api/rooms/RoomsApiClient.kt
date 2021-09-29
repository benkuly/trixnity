package net.folivo.trixnity.client.api.rooms

import com.benasher44.uuid.uuid4
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.e
import net.folivo.trixnity.client.api.rooms.CreateRoomRequest.Invite3Pid
import net.folivo.trixnity.client.api.rooms.CreateRoomRequest.Preset
import net.folivo.trixnity.client.api.rooms.Direction.FORWARD
import net.folivo.trixnity.client.api.rooms.JoinRoomRequest.ThirdParty
import net.folivo.trixnity.client.api.unsupportedEventType
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.crypto.Signed
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import kotlin.reflect.KClass

class RoomsApiClient(
    val httpClient: HttpClient,
    val json: Json,
    private val contentMappings: EventContentSerializerMappings
) {

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-event-eventid">matrix spec</a>
     */
    @ExperimentalSerializationApi
    suspend fun getEvent(
        roomId: RoomId,
        eventId: EventId,
        asUserId: UserId? = null
    ): Event<*> {
        return httpClient.get {
            url("/_matrix/client/r0/rooms/${roomId.e()}/event/${eventId.e()}")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-state-eventtype-statekey">matrix spec</a>
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend inline fun <reified C : StateEventContent> getStateEvent(
        roomId: RoomId,
        stateKey: String = "",
        asUserId: UserId? = null
    ): C {
        return getStateEvent(C::class, roomId, stateKey, asUserId)
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-state-eventtype-statekey">matrix spec</a>
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun <C : StateEventContent> getStateEvent(
        stateEventContentClass: KClass<C>,
        roomId: RoomId,
        stateKey: String = "",
        asUserId: UserId? = null
    ): C {
        val eventType =
            contentMappings.state.find { it.kClass == stateEventContentClass }?.type
                ?: throw IllegalArgumentException(unsupportedEventType(stateEventContentClass))
        val responseBody = httpClient.get<String> {
            url("/_matrix/client/r0/rooms/${roomId.e()}/state/$eventType/$stateKey")
            parameter("user_id", asUserId)
        }
        val serializer = json.serializersModule.getContextual(stateEventContentClass)
        requireNotNull(serializer)
        return json.decodeFromString(serializer, responseBody)

    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-state">matrix spec</a>
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getState(roomId: RoomId, asUserId: UserId? = null): Flow<StateEvent<*>> {
        val responseBody = httpClient.get<String> {
            url("/_matrix/client/r0/rooms/${roomId.e()}/state")
            parameter("user_id", asUserId)
        }
        val serializer = json.serializersModule.getContextual(StateEvent::class)
        requireNotNull(serializer)
        return json.decodeFromString(ListSerializer(serializer), responseBody).asFlow()
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-members">matrix spec</a>
     */
    suspend fun getMembers(
        roomId: RoomId,
        at: String? = null,
        membership: Membership? = null,
        notMembership: Membership? = null,
        asUserId: UserId? = null
    ): Flow<StateEvent<MemberEventContent>> {
        return httpClient.get<GetMembersResponse> {
            url("/_matrix/client/r0/rooms/${roomId.e()}/members")
            parameter("at", at)
            parameter("membership", membership?.value)
            parameter("not_membership", notMembership?.value)
            parameter("user_id", asUserId)
        }.chunk.asFlow()
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-joined-members">matrix spec</a>
     */
    suspend fun getJoinedMembers(
        roomId: RoomId,
        asUserId: UserId? = null
    ): GetJoinedMembersResponse {
        return httpClient.get {
            url("/_matrix/client/r0/rooms/${roomId.e()}/joined_members")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-rooms-roomid-messages">matrix spec</a>
     */
    suspend fun getEvents(
        roomId: RoomId,
        from: String,
        dir: Direction = FORWARD,
        to: String? = null,
        limit: Long = 10,
        filter: String? = null,
        asUserId: UserId? = null
    ): GetEventsResponse {
        return httpClient.get {
            url("/_matrix/client/r0/rooms/${roomId.e()}/messages")
            parameter("from", from)
            parameter("to", to)
            parameter("dir", dir.value)
            parameter("limit", limit.toString())
            parameter("filter", filter)
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-rooms-roomid-state-eventtype-statekey">matrix spec</a>
     */
    suspend fun sendStateEvent(
        roomId: RoomId,
        eventContent: StateEventContent,
        stateKey: String? = "",
        asUserId: UserId? = null
    ): EventId {
        val eventType = contentMappings.state.find { it.kClass.isInstance(eventContent) }?.type
            ?: throw IllegalArgumentException(unsupportedEventType(eventContent::class))
        return httpClient.put<SendEventResponse> {
            url("/_matrix/client/r0/rooms/${roomId.e()}/state/$eventType/$stateKey")
            parameter("user_id", asUserId)
            body = eventContent
        }.eventId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-rooms-roomid-send-eventtype-txnid">matrix spec</a>
     */
    suspend fun sendMessageEvent(
        roomId: RoomId,
        eventContent: MessageEventContent,
        txnId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): EventId {
        val eventType = contentMappings.room.find { it.kClass.isInstance(eventContent) }?.type
            ?: throw IllegalArgumentException(unsupportedEventType(eventContent::class))
        return httpClient.put<SendEventResponse> {
            url("/_matrix/client/r0/rooms/${roomId.e()}/send/$eventType/$txnId")
            parameter("user_id", asUserId)
            body = eventContent
        }.eventId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-rooms-roomid-redact-eventid-txnid">matrix spec</a>
     */
    suspend fun sendRedactEvent(
        roomId: RoomId,
        eventId: EventId,
        reason: String? = null,
        txnId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): EventId {
        return httpClient.put<SendEventResponse> {
            url("/_matrix/client/r0/rooms/${roomId.e()}/redact/${eventId.e()}/$txnId")
            parameter("user_id", asUserId)
            body = if (reason != null) mapOf("reason" to reason) else mapOf()
        }.eventId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-createroom">matrix spec</a>
     */
    suspend fun createRoom(
        visibility: Visibility = Visibility.PRIVATE,
        roomAliasId: RoomAliasId? = null,
        name: String? = null,
        topic: String? = null,
        invite: Set<UserId>? = null,
        invite3Pid: Set<Invite3Pid>? = null,
        roomVersion: String? = null,
        creationContent: CreateEventContent? = null,
        initialState: List<StateEvent<*>>? = null,
        preset: Preset? = null,
        isDirect: Boolean? = null,
        powerLevelContentOverride: PowerLevelsEventContent? = null,
        asUserId: UserId? = null
    ): RoomId {
        return httpClient.post<CreateRoomResponse> {
            url("/_matrix/client/r0/createRoom")
            parameter("user_id", asUserId)
            body = CreateRoomRequest(
                visibility,
                roomAliasId?.localpart,
                name,
                topic,
                invite,
                invite3Pid,
                roomVersion,
                creationContent,
                initialState,
                preset,
                isDirect,
                powerLevelContentOverride
            )
        }.roomId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-directory-room-roomalias">matrix spec</a>
     */
    suspend fun setRoomAlias(
        roomId: RoomId,
        roomAliasId: RoomAliasId,
        asUserId: UserId? = null
    ) {
        return httpClient.put {
            url("/_matrix/client/r0/directory/room/${roomAliasId.e()}")
            parameter("user_id", asUserId)
            body = SetRoomAliasRequest(roomId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-directory-room-roomalias">matrix spec</a>
     */
    suspend fun getRoomAlias(
        roomAliasId: RoomAliasId,
        asUserId: UserId? = null
    ): GetRoomAliasResponse {
        return httpClient.get {
            url("/_matrix/client/r0/directory/room/${roomAliasId.e()}")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#delete-matrix-client-r0-directory-room-roomalias">matrix spec</a>
     */
    suspend fun deleteRoomAlias(
        roomAliasId: RoomAliasId,
        asUserId: UserId? = null
    ) {
        return httpClient.delete {
            url("/_matrix/client/r0/directory/room/${roomAliasId.e()}")
            parameter("user_id", asUserId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-joined-rooms">matrix spec</a>
     */
    suspend fun getJoinedRooms(asUserId: UserId? = null): Flow<RoomId> {
        return httpClient.get<GetJoinedRoomsResponse> {
            url("/_matrix/client/r0/joined_rooms")
            parameter("user_id", asUserId)
        }.joinedRooms.asFlow()
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-rooms-roomid-invite">matrix spec</a>
     */
    suspend fun inviteUser(
        roomId: RoomId,
        userId: UserId,
        asUserId: UserId? = null
    ) {
        return httpClient.post {
            url("/_matrix/client/r0/rooms/${roomId.e()}/invite")
            parameter("user_id", asUserId)
            body = InviteUserRequest(userId)
        }
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-join-roomidoralias">matrix spec</a>
     */
    suspend fun joinRoom(
        roomId: RoomId,
        serverNames: Set<String>? = null,
        thirdPartySigned: Signed<ThirdParty, String>? = null,
        asUserId: UserId? = null
    ): RoomId {
        return httpClient.post<JoinRoomResponse> {
            url("/_matrix/client/r0/join/${roomId.e()}")
            serverNames?.forEach { parameter("server_name", it) }
            parameter("user_id", asUserId)
            body = JoinRoomRequest(thirdPartySigned)
        }.roomId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-join-roomidoralias">matrix spec</a>
     */
    suspend fun joinRoom(
        roomAliasId: RoomAliasId,
        serverNames: Set<String>? = null,
        thirdPartySigned: Signed<ThirdParty, String>? = null,
        asUserId: UserId? = null
    ): RoomId {
        return httpClient.post<JoinRoomResponse> {
            url("/_matrix/client/r0/join/${roomAliasId.e()}")
            serverNames?.forEach { parameter("server_name", it) }
            parameter("user_id", asUserId)
            body = JoinRoomRequest(thirdPartySigned)
        }.roomId
    }

    /**
     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-rooms-roomid-leave">matrix spec</a>
     */
    suspend fun leaveRoom(
        roomId: RoomId,
        asUserId: UserId? = null
    ) {
        return httpClient.post {
            url("/_matrix/client/r0/rooms/${roomId.e()}/leave")
            parameter("user_id", asUserId)
        }
    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-rooms-roomid-forget">matrix spec</a>
//     */
//    fun forgetRoom() {
//        // TODO implement
//    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-rooms-roomid-kick">matrix spec</a>
//     */
//    fun kickUser() {
//        // TODO implement
//    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-rooms-roomid-ban">matrix spec</a>
//     */
//    fun banUser() {
//        // TODO implement
//    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-rooms-roomid-unban">matrix spec</a>
//     */
//    fun unbanUser() {
//        // TODO implement
//    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#get-matrix-client-r0-directory-list-room-roomid">matrix spec</a>
//     */
//    fun getVisibility() {
//        // TODO implement
//    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#put-matrix-client-r0-directory-list-room-roomid">matrix spec</a>
//     */
//    fun setVisibility() {
//        // TODO implement
//    }

//    /**
//     * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#post-matrix-client-r0-publicrooms">matrix spec</a>
//     */
//    fun getPublicRooms() {
//        // TODO implement
//    }
}