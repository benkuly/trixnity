package net.folivo.trixnity.client.api

import com.benasher44.uuid.uuid4
import io.ktor.client.request.*
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.api.model.rooms.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.Signed
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlin.reflect.KClass

class RoomsApiClient(
    val httpClient: MatrixHttpClient,
    val json: Json,
    private val contentMappings: EventContentSerializerMappings
) {

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3roomsroomideventeventid">matrix spec</a>
     */
    @ExperimentalSerializationApi
    suspend fun getEvent(
        roomId: RoomId,
        eventId: EventId,
        asUserId: UserId? = null
    ): Result<Event<*>> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/rooms/${roomId.e()}/event/${eventId.e()}")
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3roomsroomidstateeventtypestatekey">matrix spec</a>
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun <C : StateEventContent> getStateEvent(
        stateEventContentClass: KClass<C>,
        roomId: RoomId,
        stateKey: String = "",
        asUserId: UserId? = null
    ): Result<C> {
        val mapping = contentMappings.state.find { it.kClass == stateEventContentClass }
            ?: throw IllegalArgumentException(unsupportedEventType(stateEventContentClass))
        return httpClient.request<String> {
            method = Get
            url("/_matrix/client/v3/rooms/${roomId.e()}/state/${mapping.type}/$stateKey")
            parameter("user_id", asUserId)
        }.mapCatching {
            @Suppress("UNCHECKED_CAST")
            val serializer = mapping.serializer as KSerializer<C>
            json.decodeFromString(serializer, it)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3roomsroomidstateeventtypestatekey">matrix spec</a>
     */
    suspend inline fun <reified C : StateEventContent> getStateEvent(
        roomId: RoomId,
        stateKey: String = "",
        asUserId: UserId? = null
    ): Result<C> = getStateEvent(C::class, roomId, stateKey, asUserId)

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3roomsroomidstate">matrix spec</a>
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun getState(roomId: RoomId, asUserId: UserId? = null): Result<Flow<StateEvent<*>>> =
        httpClient.request<String> {
            method = Get
            url("/_matrix/client/v3/rooms/${roomId.e()}/state")
            parameter("user_id", asUserId)
        }.mapCatching {
            val serializer = json.serializersModule.getContextual(StateEvent::class)
            requireNotNull(serializer)
            json.decodeFromString(ListSerializer(serializer), it).asFlow()
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3roomsroomidmembers">matrix spec</a>
     */
    suspend fun getMembers(
        roomId: RoomId,
        at: String? = null,
        membership: Membership? = null,
        notMembership: Membership? = null,
        asUserId: UserId? = null
    ): Result<Flow<StateEvent<MemberEventContent>>> =
        httpClient.request<GetMembersResponse> {
            method = Get
            url("/_matrix/client/v3/rooms/${roomId.e()}/members")
            parameter("at", at)
            parameter("membership", membership?.value)
            parameter("not_membership", notMembership?.value)
            parameter("user_id", asUserId)
        }.mapCatching { it.chunk.asFlow() }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3roomsroomidjoined_members">matrix spec</a>
     */
    suspend fun getJoinedMembers(
        roomId: RoomId,
        asUserId: UserId? = null
    ): Result<GetJoinedMembersResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/rooms/${roomId.e()}/joined_members")
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3roomsroomidmessages">matrix spec</a>
     */
    suspend fun getEvents(
        roomId: RoomId,
        from: String,
        dir: Direction = Direction.FORWARD,
        to: String? = null,
        limit: Long = 10,
        filter: String? = null,
        asUserId: UserId? = null
    ): Result<GetEventsResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/rooms/${roomId.e()}/messages")
            parameter("from", from)
            parameter("to", to)
            parameter("dir", dir.value)
            parameter("limit", limit.toString())
            parameter("filter", filter)
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3roomsroomidstateeventtypestatekey">matrix spec</a>
     */
    suspend fun sendStateEvent(
        roomId: RoomId,
        eventContent: StateEventContent,
        stateKey: String? = "",
        asUserId: UserId? = null
    ): Result<EventId> {
        val eventType = contentMappings.state.find { it.kClass.isInstance(eventContent) }?.type
            ?: throw IllegalArgumentException(unsupportedEventType(eventContent::class))
        return httpClient.request<SendEventResponse> {
            method = Put
            url("/_matrix/client/v3/rooms/${roomId.e()}/state/$eventType/$stateKey")
            parameter("user_id", asUserId)
            body = eventContent
        }.mapCatching { it.eventId }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3roomsroomidsendeventtypetxnid">matrix spec</a>
     */
    suspend fun sendMessageEvent(
        roomId: RoomId,
        eventContent: MessageEventContent,
        txnId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): Result<EventId> {
        val eventType = contentMappings.message.find { it.kClass.isInstance(eventContent) }?.type
            ?: throw IllegalArgumentException(unsupportedEventType(eventContent::class))
        return httpClient.request<SendEventResponse> {
            method = Put
            url("/_matrix/client/v3/rooms/${roomId.e()}/send/$eventType/$txnId")
            parameter("user_id", asUserId)
            body = eventContent
        }.mapCatching { it.eventId }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3roomsroomidredacteventidtxnid">matrix spec</a>
     */
    suspend fun redactEvent(
        roomId: RoomId,
        eventId: EventId,
        reason: String? = null,
        txnId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): Result<EventId> =
        httpClient.request<SendEventResponse> {
            method = Put
            url("/_matrix/client/v3/rooms/${roomId.e()}/redact/${eventId.e()}/$txnId")
            parameter("user_id", asUserId)
            body = RedactEventRequest(reason)
        }.mapCatching { it.eventId }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3createroom">matrix spec</a>
     */
    suspend fun createRoom(
        visibility: Visibility = Visibility.PRIVATE,
        roomAliasId: RoomAliasId? = null,
        name: String? = null,
        topic: String? = null,
        invite: Set<UserId>? = null,
        invite3Pid: Set<CreateRoomRequest.Invite3Pid>? = null,
        roomVersion: String? = null,
        creationContent: CreateEventContent? = null,
        initialState: List<Event.InitialStateEvent<*>>? = null,
        preset: CreateRoomRequest.Preset? = null,
        isDirect: Boolean? = null,
        powerLevelContentOverride: PowerLevelsEventContent? = null,
        asUserId: UserId? = null
    ): Result<RoomId> =
        httpClient.request<CreateRoomResponse> {
            method = Post
            url("/_matrix/client/v3/createRoom")
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
        }.mapCatching { it.roomId }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3directoryroomroomalias">matrix spec</a>
     */
    suspend fun setRoomAlias(
        roomId: RoomId,
        roomAliasId: RoomAliasId,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request {
            method = Put
            url("/_matrix/client/v3/directory/room/${roomAliasId.e()}")
            parameter("user_id", asUserId)
            body = SetRoomAliasRequest(roomId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3directoryroomroomalias">matrix spec</a>
     */
    suspend fun getRoomAlias(
        roomAliasId: RoomAliasId,
        asUserId: UserId? = null
    ): Result<GetRoomAliasResponse> =
        httpClient.request {
            method = Get
            url("/_matrix/client/v3/directory/room/${roomAliasId.e()}")
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#delete_matrixclientv3directoryroomroomalias">matrix spec</a>
     */
    suspend fun deleteRoomAlias(
        roomAliasId: RoomAliasId,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request {
            method = Delete
            url("/_matrix/client/v3/directory/room/${roomAliasId.e()}")
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3joined_rooms">matrix spec</a>
     */
    suspend fun getJoinedRooms(asUserId: UserId? = null): Result<Flow<RoomId>> =
        httpClient.request<GetJoinedRoomsResponse> {
            method = Get
            url("/_matrix/client/v3/joined_rooms")
            parameter("user_id", asUserId)
        }.mapCatching { it.joinedRooms.asFlow() }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3roomsroomidinvite">matrix spec</a>
     */
    suspend fun inviteUser(
        roomId: RoomId,
        userId: UserId,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/rooms/${roomId.e()}/invite")
            parameter("user_id", asUserId)
            body = InviteUserRequest(userId, reason)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3roomsroomidkick">matrix spec</a>
     */
    suspend fun kickUser(
        roomId: RoomId,
        userId: UserId,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/rooms/${roomId.e()}/kick")
            parameter("user_id", asUserId)
            body = KickUserRequest(userId, reason)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3roomsroomidban">matrix spec</a>
     */
    suspend fun banUser(
        roomId: RoomId,
        userId: UserId,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/rooms/${roomId.e()}/ban")
            parameter("user_id", asUserId)
            body = BanUserRequest(userId, reason)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3roomsroomidunban">matrix spec</a>
     */
    suspend fun unbanUser(
        roomId: RoomId,
        userId: UserId,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/rooms/${roomId.e()}/unban")
            parameter("user_id", asUserId)
            body = UnbanUserRequest(userId, reason)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3joinroomidoralias">matrix spec</a>
     */
    suspend fun joinRoom(
        roomId: RoomId,
        serverNames: Set<String>? = null,
        reason: String? = null,
        thirdPartySigned: Signed<JoinRoomRequest.ThirdParty, String>? = null,
        asUserId: UserId? = null
    ): Result<RoomId> =
        httpClient.request<JoinRoomResponse> {
            method = Post
            url("/_matrix/client/v3/join/${roomId.e()}")
            serverNames?.forEach { parameter("server_name", it) }
            parameter("user_id", asUserId)
            body = JoinRoomRequest(reason, thirdPartySigned)
        }.mapCatching { it.roomId }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3joinroomidoralias">matrix spec</a>
     */
    suspend fun joinRoom(
        roomAliasId: RoomAliasId,
        serverNames: Set<String>? = null,
        reason: String? = null,
        thirdPartySigned: Signed<JoinRoomRequest.ThirdParty, String>? = null,
        asUserId: UserId? = null
    ): Result<RoomId> =
        httpClient.request<JoinRoomResponse> {
            method = Post
            url("/_matrix/client/v3/join/${roomAliasId.e()}")
            serverNames?.forEach { parameter("server_name", it) }
            parameter("user_id", asUserId)
            body = JoinRoomRequest(reason, thirdPartySigned)
        }.mapCatching { it.roomId }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3knockroomidoralias">matrix spec</a>
     */
    suspend fun knockRoom(
        roomId: RoomId,
        serverNames: Set<String>? = null,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<RoomId> =
        httpClient.request<KnockRoomResponse> {
            method = Post
            url("/_matrix/client/v3/knock/${roomId.e()}")
            serverNames?.forEach { parameter("server_name", it) }
            parameter("user_id", asUserId)
            body = KnockRoomRequest(reason)
        }.mapCatching { it.roomId }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3knockroomidoralias">matrix spec</a>
     */
    suspend fun knockRoom(
        roomAliasId: RoomAliasId,
        serverNames: Set<String>? = null,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<RoomId> =
        httpClient.request<KnockRoomResponse> {
            method = Post
            url("/_matrix/client/v3/knock/${roomAliasId.e()}")
            serverNames?.forEach { parameter("server_name", it) }
            parameter("user_id", asUserId)
            body = KnockRoomRequest(reason)
        }.mapCatching { it.roomId }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3roomsroomidforget">matrix spec</a>
     */
    suspend fun forgetRoom(
        roomId: RoomId,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/rooms/${roomId.e()}/forget")
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3roomsroomidleave">matrix spec</a>
     */
    suspend fun leaveRoom(
        roomId: RoomId,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<Unit> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/rooms/${roomId.e()}/leave")
            parameter("user_id", asUserId)
            body = LeaveRoomRequest(reason)
        }


    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3roomsroomidreceiptreceipttypeeventid">matrix spec</a>
     */
    suspend fun setReceipt(
        roomId: RoomId,
        eventId: EventId,
        receiptType: ReceiptType = ReceiptType.READ,
        asUserId: UserId? = null,
    ): Result<Unit> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/rooms/${roomId.e()}/receipt/${receiptType.value}/${eventId.e()}")
            parameter("user_id", asUserId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#post_matrixclientv3roomsroomidread_markers">matrix spec</a>
     */
    suspend fun setReadMarkers(
        roomId: RoomId,
        eventId: EventId,
        asUserId: UserId? = null,
    ): Result<Unit> =
        httpClient.request {
            method = Post
            url("/_matrix/client/v3/rooms/${roomId.e()}/read_markers")
            parameter("user_id", asUserId)
            body = FullyReadRequest(eventId, eventId)
        }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3useruseridroomsroomidaccount_datatype">matrix spec</a>
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun <C : RoomAccountDataEventContent> getAccountData(
        accountDataEventContentClass: KClass<C>,
        roomId: RoomId,
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<C> {
        val mapping = contentMappings.roomAccountData.find { it.kClass == accountDataEventContentClass }
            ?: throw IllegalArgumentException(unsupportedEventType(accountDataEventContentClass))
        val eventType = if (key.isEmpty()) mapping.type else mapping.type + key
        return httpClient.request<String> {
            method = Get
            url("/_matrix/client/v3/user/${userId.e()}/rooms/${roomId.e()}/account_data/$eventType")
            parameter("user_id", asUserId)
        }.mapCatching {
            @Suppress("UNCHECKED_CAST")
            val serializer = mapping.serializer as KSerializer<C>
            json.decodeFromString(serializer, it)
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#get_matrixclientv3useruseridroomsroomidaccount_datatype">matrix spec</a>
     */
    suspend inline fun <reified C : RoomAccountDataEventContent> getAccountData(
        roomId: RoomId,
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<C> = getAccountData(C::class, roomId, userId, key, asUserId)

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3useruseridroomsroomidaccount_datatype">matrix spec</a>
     */
    suspend fun <C : RoomAccountDataEventContent> setAccountData(
        content: C,
        roomId: RoomId,
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<Unit> {
        val eventType =
            contentMappings.roomAccountData.find { it.kClass.isInstance(content) }?.type
                ?.let { type -> if (key.isEmpty()) type else type + key }
                ?: throw IllegalArgumentException(unsupportedEventType(content::class))
        return httpClient.request {
            method = Put
            url("/_matrix/client/v3/user/${userId.e()}/rooms/${roomId.e()}/account_data/$eventType")
            parameter("user_id", asUserId)
            body = content
        }
    }

    /**
     * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#put_matrixclientv3roomsroomidtypinguserid">matrix spec</a>
     */
    suspend fun setUserIsTyping(
        roomId: RoomId,
        userId: UserId,
        typing: Boolean,
        timeout: Int? = null,
        asUserId: UserId? = null,
    ): Result<Unit> =
        httpClient.request {
            method = Put
            url("/_matrix/client/v3/rooms/${roomId.e()}/typing/${userId.e()}")
            parameter("user_id", asUserId)
            body = TypingRequest(typing, timeout)
        }
}