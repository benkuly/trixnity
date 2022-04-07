package net.folivo.trixnity.clientserverapi.client

import com.benasher44.uuid.uuid4
import net.folivo.trixnity.api.client.e
import net.folivo.trixnity.clientserverapi.model.rooms.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.TagEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.contentSerializer
import net.folivo.trixnity.core.serialization.events.fromClass

interface IRoomsApiClient {

    val contentMappings: EventContentSerializerMappings

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomideventeventid">matrix spec</a>
     */
    suspend fun getEvent(
        roomId: RoomId,
        eventId: EventId,
        asUserId: UserId? = null
    ): Result<Event<*>>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidstateeventtypestatekey">matrix spec</a>
     */
    suspend fun getStateEvent(
        type: String,
        roomId: RoomId,
        stateKey: String = "",
        asUserId: UserId? = null
    ): Result<StateEventContent>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidstate">matrix spec</a>
     */
    suspend fun getState(roomId: RoomId, asUserId: UserId? = null): Result<List<StateEvent<*>>>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidmembers">matrix spec</a>
     */
    suspend fun getMembers(
        roomId: RoomId,
        at: String? = null,
        membership: Membership? = null,
        notMembership: Membership? = null,
        asUserId: UserId? = null
    ): Result<Set<StateEvent<MemberEventContent>>>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidjoined_members">matrix spec</a>
     */
    suspend fun getJoinedMembers(
        roomId: RoomId,
        asUserId: UserId? = null
    ): Result<GetJoinedMembers.Response>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidmessages">matrix spec</a>
     */
    suspend fun getEvents(
        roomId: RoomId,
        from: String,
        dir: GetEvents.Direction = GetEvents.Direction.FORWARD,
        to: String? = null,
        limit: Long? = null,
        filter: String? = null,
        asUserId: UserId? = null
    ): Result<GetEvents.Response>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3roomsroomidstateeventtypestatekey">matrix spec</a>
     */
    suspend fun sendStateEvent(
        roomId: RoomId,
        eventContent: StateEventContent,
        stateKey: String = "",
        asUserId: UserId? = null
    ): Result<EventId>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3roomsroomidsendeventtypetxnid">matrix spec</a>
     */
    suspend fun sendMessageEvent(
        roomId: RoomId,
        eventContent: MessageEventContent,
        txnId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): Result<EventId>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3roomsroomidredacteventidtxnid">matrix spec</a>
     */
    suspend fun redactEvent(
        roomId: RoomId,
        eventId: EventId,
        reason: String? = null,
        txnId: String = uuid4().toString(),
        asUserId: UserId? = null
    ): Result<EventId>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3createroom">matrix spec</a>
     */
    suspend fun createRoom(
        visibility: DirectoryVisibility = DirectoryVisibility.PRIVATE,
        roomAliasId: RoomAliasId? = null,
        name: String? = null,
        topic: String? = null,
        invite: Set<UserId>? = null,
        invite3Pid: Set<CreateRoom.Request.Invite3Pid>? = null,
        roomVersion: String? = null,
        creationContent: CreateEventContent? = null,
        initialState: List<Event.InitialStateEvent<*>>? = null,
        preset: CreateRoom.Request.Preset? = null,
        isDirect: Boolean? = null,
        powerLevelContentOverride: PowerLevelsEventContent? = null,
        asUserId: UserId? = null
    ): Result<RoomId>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3directoryroomroomalias">matrix spec</a>
     */
    suspend fun setRoomAlias(
        roomId: RoomId,
        roomAliasId: RoomAliasId,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3directoryroomroomalias">matrix spec</a>
     */
    suspend fun getRoomAlias(
        roomAliasId: RoomAliasId,
    ): Result<GetRoomAlias.Response>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidaliases">matrix spec</a>
     */
    suspend fun getRoomAliases(
        roomId: RoomId,
    ): Result<Set<RoomAliasId>>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3directoryroomroomalias">matrix spec</a>
     */
    suspend fun deleteRoomAlias(
        roomAliasId: RoomAliasId,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3joined_rooms">matrix spec</a>
     */
    suspend fun getJoinedRooms(asUserId: UserId? = null): Result<Set<RoomId>>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidinvite">matrix spec</a>
     */
    suspend fun inviteUser(
        roomId: RoomId,
        userId: UserId,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidkick">matrix spec</a>
     */
    suspend fun kickUser(
        roomId: RoomId,
        userId: UserId,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidban">matrix spec</a>
     */
    suspend fun banUser(
        roomId: RoomId,
        userId: UserId,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidunban">matrix spec</a>
     */
    suspend fun unbanUser(
        roomId: RoomId,
        userId: UserId,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3joinroomidoralias">matrix spec</a>
     */
    suspend fun joinRoom(
        roomId: RoomId,
        serverNames: Set<String>? = null,
        reason: String? = null,
        thirdPartySigned: Signed<JoinRoom.Request.ThirdParty, String>? = null,
        asUserId: UserId? = null
    ): Result<RoomId>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3joinroomidoralias">matrix spec</a>
     */
    suspend fun joinRoom(
        roomAliasId: RoomAliasId,
        serverNames: Set<String>? = null,
        reason: String? = null,
        thirdPartySigned: Signed<JoinRoom.Request.ThirdParty, String>? = null,
        asUserId: UserId? = null
    ): Result<RoomId>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3knockroomidoralias">matrix spec</a>
     */
    suspend fun knockRoom(
        roomId: RoomId,
        serverNames: Set<String>? = null,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<RoomId>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3knockroomidoralias">matrix spec</a>
     */
    suspend fun knockRoom(
        roomAliasId: RoomAliasId,
        serverNames: Set<String>? = null,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<RoomId>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidforget">matrix spec</a>
     */
    suspend fun forgetRoom(
        roomId: RoomId,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidleave">matrix spec</a>
     */
    suspend fun leaveRoom(
        roomId: RoomId,
        reason: String? = null,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidreceiptreceipttypeeventid">matrix spec</a>
     */
    suspend fun setReceipt(
        roomId: RoomId,
        eventId: EventId,
        receiptType: SetReceipt.ReceiptType = SetReceipt.ReceiptType.READ,
        asUserId: UserId? = null,
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidread_markers">matrix spec</a>
     */
    suspend fun setReadMarkers(
        roomId: RoomId,
        fullyRead: EventId,
        read: EventId? = null,
        asUserId: UserId? = null,
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3roomsroomidtypinguserid">matrix spec</a>
     */
    suspend fun setTyping(
        roomId: RoomId,
        userId: UserId,
        typing: Boolean,
        timeout: Long? = null,
        asUserId: UserId? = null,
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridroomsroomidaccount_datatype">matrix spec</a>
     */
    suspend fun getAccountData(
        type: String,
        roomId: RoomId,
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<RoomAccountDataEventContent>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3useruseridroomsroomidaccount_datatype">matrix spec</a>
     */
    suspend fun setAccountData(
        content: RoomAccountDataEventContent,
        roomId: RoomId,
        userId: UserId,
        key: String = "",
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3directorylistroomroomid">matrix spec</a>
     */
    suspend fun getDirectoryVisibility(
        roomId: RoomId,
    ): Result<DirectoryVisibility>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3directorylistroomroomid">matrix spec</a>
     */
    suspend fun setDirectoryVisibility(
        roomId: RoomId,
        visibility: DirectoryVisibility,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3publicrooms">matrix spec</a>
     */
    suspend fun getPublicRooms(
        limit: Long? = null,
        server: String? = null,
        since: String? = null
    ): Result<GetPublicRoomsResponse>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3publicrooms">matrix spec</a>
     */
    suspend fun getPublicRooms(
        limit: Long? = null,
        server: String? = null,
        since: String? = null,
        filter: GetPublicRoomsWithFilter.Request.Filter? = null,
        includeAllNetworks: Boolean? = null,
        thirdPartyInstanceId: String? = null,
        asUserId: UserId? = null
    ): Result<GetPublicRoomsResponse>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridroomsroomidtags">matrix spec</a>
     */
    suspend fun getTags(
        userId: UserId,
        roomId: RoomId,
        asUserId: UserId? = null
    ): Result<TagEventContent>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3useruseridroomsroomidtagstag">matrix spec</a>
     */
    suspend fun setTag(
        userId: UserId,
        roomId: RoomId,
        tag: String,
        tagValue: TagEventContent.Tag,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3useruseridroomsroomidtagstag">matrix spec</a>
     */
    suspend fun deleteTag(
        userId: UserId,
        roomId: RoomId,
        tag: String,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidcontexteventid">matrix spec</a>
     */
    suspend fun getEventContext(
        roomId: RoomId,
        eventId: EventId,
        filter: String? = null,
        limit: Long? = null,
        asUserId: UserId? = null
    ): Result<GetEventContext.Response>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidreporteventid">matrix spec</a>
     */
    suspend fun reportEvent(
        roomId: RoomId,
        eventId: EventId,
        reason: String? = null,
        score: Long? = null,
        asUserId: UserId? = null
    ): Result<Unit>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidupgrade">matrix spec</a>
     */
    suspend fun upgradeRoom(
        roomId: RoomId,
        version: String,
        asUserId: UserId? = null
    ): Result<RoomId>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv1roomsroomidhierarchy">matrix spec</a>
     */
    suspend fun getHierarchy(
        roomId: RoomId,
        from: String,
        limit: Long? = null,
        maxDepth: Long? = null,
        suggestedOnly: Boolean = false,
        asUserId: UserId? = null
    ): Result<GetHierarchy.Response>
}

class RoomsApiClient(
    private val httpClient: MatrixClientServerApiHttpClient,
    override val contentMappings: EventContentSerializerMappings
) : IRoomsApiClient {

    override suspend fun getEvent(
        roomId: RoomId,
        eventId: EventId,
        asUserId: UserId?
    ): Result<Event<*>> =
        httpClient.request(GetEvent(roomId.e(), eventId.e(), asUserId))

    override suspend fun getStateEvent(
        type: String,
        roomId: RoomId,
        stateKey: String,
        asUserId: UserId?
    ): Result<StateEventContent> =
        httpClient.request(GetStateEvent(roomId.e(), type, stateKey.e(), asUserId))

    override suspend fun getState(roomId: RoomId, asUserId: UserId?): Result<List<StateEvent<*>>> =
        httpClient.request(GetState(roomId.e(), asUserId))

    override suspend fun getMembers(
        roomId: RoomId,
        at: String?,
        membership: Membership?,
        notMembership: Membership?,
        asUserId: UserId?
    ): Result<Set<StateEvent<MemberEventContent>>> =
        httpClient.request(GetMembers(roomId.e(), at, membership, notMembership, asUserId))
            .mapCatching { response ->
                response.chunk.asSequence()
                    .filter { it.content is MemberEventContent }
                    .map {
                        @Suppress("UNCHECKED_CAST")
                        it as StateEvent<MemberEventContent>
                    }
                    .toSet()
            }

    override suspend fun getJoinedMembers(
        roomId: RoomId,
        asUserId: UserId?
    ): Result<GetJoinedMembers.Response> =
        httpClient.request(GetJoinedMembers(roomId.e(), asUserId))

    override suspend fun getEvents(
        roomId: RoomId,
        from: String,
        dir: GetEvents.Direction,
        to: String?,
        limit: Long?,
        filter: String?,
        asUserId: UserId?
    ): Result<GetEvents.Response> =
        httpClient.request(GetEvents(roomId.e(), from, to, dir, limit, filter, asUserId))

    override suspend fun sendStateEvent(
        roomId: RoomId,
        eventContent: StateEventContent,
        stateKey: String,
        asUserId: UserId?
    ): Result<EventId> {
        val eventType = contentMappings.state.contentSerializer(eventContent).first
        return httpClient.request(SendStateEvent(roomId.e(), eventType, stateKey.e(), asUserId), eventContent)
            .mapCatching { it.eventId }
    }

    override suspend fun sendMessageEvent(
        roomId: RoomId,
        eventContent: MessageEventContent,
        txnId: String,
        asUserId: UserId?
    ): Result<EventId> {
        val eventType = contentMappings.message.contentSerializer(eventContent).first
        return httpClient.request(SendMessageEvent(roomId.e(), eventType, txnId, asUserId), eventContent)
            .mapCatching { it.eventId }
    }

    override suspend fun redactEvent(
        roomId: RoomId,
        eventId: EventId,
        reason: String?,
        txnId: String,
        asUserId: UserId?
    ): Result<EventId> =
        httpClient.request(RedactEvent(roomId.e(), eventId.e(), txnId, asUserId), RedactEvent.Request(reason))
            .mapCatching { it.eventId }

    override suspend fun createRoom(
        visibility: DirectoryVisibility,
        roomAliasId: RoomAliasId?,
        name: String?,
        topic: String?,
        invite: Set<UserId>?,
        invite3Pid: Set<CreateRoom.Request.Invite3Pid>?,
        roomVersion: String?,
        creationContent: CreateEventContent?,
        initialState: List<Event.InitialStateEvent<*>>?,
        preset: CreateRoom.Request.Preset?,
        isDirect: Boolean?,
        powerLevelContentOverride: PowerLevelsEventContent?,
        asUserId: UserId?
    ): Result<RoomId> =
        httpClient.request(
            CreateRoom(asUserId),
            CreateRoom.Request(
                visibility = visibility,
                roomAliasLocalPart = roomAliasId?.localpart,
                name = name,
                topic = topic,
                invite = invite,
                invite3Pid = invite3Pid,
                roomVersion = roomVersion,
                creationContent = creationContent,
                initialState = initialState,
                preset = preset,
                isDirect = isDirect,
                powerLevelContentOverride = powerLevelContentOverride
            )
        ).mapCatching { it.roomId }

    override suspend fun setRoomAlias(
        roomId: RoomId,
        roomAliasId: RoomAliasId,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(SetRoomAlias(roomAliasId.e(), asUserId), SetRoomAlias.Request(roomId))

    override suspend fun getRoomAlias(
        roomAliasId: RoomAliasId,
    ): Result<GetRoomAlias.Response> =
        httpClient.request(GetRoomAlias(roomAliasId.e()))

    override suspend fun getRoomAliases(
        roomId: RoomId,
    ): Result<Set<RoomAliasId>> =
        httpClient.request(GetRoomAliases(roomId.e())).map { it.aliases }

    override suspend fun deleteRoomAlias(
        roomAliasId: RoomAliasId,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(DeleteRoomAlias(roomAliasId.e(), asUserId))

    override suspend fun getJoinedRooms(asUserId: UserId?): Result<Set<RoomId>> =
        httpClient.request(GetJoinedRooms(asUserId)).mapCatching { it.joinedRooms }

    override suspend fun inviteUser(
        roomId: RoomId,
        userId: UserId,
        reason: String?,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(InviteUser(roomId.e(), asUserId), InviteUser.Request(userId, reason))

    override suspend fun kickUser(
        roomId: RoomId,
        userId: UserId,
        reason: String?,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(KickUser(roomId.e(), asUserId), KickUser.Request(userId, reason))

    override suspend fun banUser(
        roomId: RoomId,
        userId: UserId,
        reason: String?,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(BanUser(roomId.e(), asUserId), BanUser.Request(userId, reason))


    override suspend fun unbanUser(
        roomId: RoomId,
        userId: UserId,
        reason: String?,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(UnbanUser(roomId.e(), asUserId), UnbanUser.Request(userId, reason))


    override suspend fun joinRoom(
        roomId: RoomId,
        serverNames: Set<String>?,
        reason: String?,
        thirdPartySigned: Signed<JoinRoom.Request.ThirdParty, String>?,
        asUserId: UserId?
    ): Result<RoomId> =
        httpClient.request(JoinRoom(roomId.full.e(), serverNames, asUserId), JoinRoom.Request(reason, thirdPartySigned))
            .mapCatching { it.roomId }

    override suspend fun joinRoom(
        roomAliasId: RoomAliasId,
        serverNames: Set<String>?,
        reason: String?,
        thirdPartySigned: Signed<JoinRoom.Request.ThirdParty, String>?,
        asUserId: UserId?
    ): Result<RoomId> =
        httpClient.request(
            JoinRoom(roomAliasId.full.e(), serverNames, asUserId),
            JoinRoom.Request(reason, thirdPartySigned)
        ).mapCatching { it.roomId }

    override suspend fun knockRoom(
        roomId: RoomId,
        serverNames: Set<String>?,
        reason: String?,
        asUserId: UserId?
    ): Result<RoomId> =
        httpClient.request(KnockRoom(roomId.full.e(), serverNames, asUserId), KnockRoom.Request(reason))
            .mapCatching { it.roomId }

    override suspend fun knockRoom(
        roomAliasId: RoomAliasId,
        serverNames: Set<String>?,
        reason: String?,
        asUserId: UserId?
    ): Result<RoomId> =
        httpClient.request(KnockRoom(roomAliasId.full.e(), serverNames, asUserId), KnockRoom.Request(reason))
            .mapCatching { it.roomId }

    override suspend fun forgetRoom(
        roomId: RoomId,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(ForgetRoom(roomId.e(), asUserId))

    override suspend fun leaveRoom(
        roomId: RoomId,
        reason: String?,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(LeaveRoom(roomId.e(), asUserId), LeaveRoom.Request(reason))

    override suspend fun setReceipt(
        roomId: RoomId,
        eventId: EventId,
        receiptType: SetReceipt.ReceiptType,
        asUserId: UserId?,
    ): Result<Unit> =
        httpClient.request(SetReceipt(roomId.e(), receiptType, eventId.e(), asUserId))

    override suspend fun setReadMarkers(
        roomId: RoomId,
        fullyRead: EventId,
        read: EventId?,
        asUserId: UserId?,
    ): Result<Unit> =
        httpClient.request(SetReadMarkers(roomId.e(), asUserId), SetReadMarkers.Request(fullyRead, read))

    override suspend fun setTyping(
        roomId: RoomId,
        userId: UserId,
        typing: Boolean,
        timeout: Long?,
        asUserId: UserId?,
    ): Result<Unit> =
        httpClient.request(SetTyping(roomId.e(), userId.e(), asUserId), SetTyping.Request(typing, timeout))

    override suspend fun getAccountData(
        type: String,
        roomId: RoomId,
        userId: UserId,
        key: String,
        asUserId: UserId?
    ): Result<RoomAccountDataEventContent> {
        val actualType = if (key.isEmpty()) type else type + key
        return httpClient.request(GetRoomAccountData(userId.e(), roomId.e(), actualType, asUserId))
    }

    override suspend fun setAccountData(
        content: RoomAccountDataEventContent,
        roomId: RoomId,
        userId: UserId,
        key: String,
        asUserId: UserId?
    ): Result<Unit> {
        val mapping = contentMappings.roomAccountData.contentSerializer(content)
        val eventType = mapping.first.let { type -> if (key.isEmpty()) type else type + key }

        return httpClient.request(SetRoomAccountData(userId.e(), roomId.e(), eventType, asUserId), content)
    }

    override suspend fun getDirectoryVisibility(
        roomId: RoomId,
    ): Result<DirectoryVisibility> =
        httpClient.request(GetDirectoryVisibility(roomId.e())).map { it.visibility }

    override suspend fun setDirectoryVisibility(
        roomId: RoomId,
        visibility: DirectoryVisibility,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(SetDirectoryVisibility(roomId.e(), asUserId), SetDirectoryVisibility.Request(visibility))

    override suspend fun getPublicRooms(
        limit: Long?,
        server: String?,
        since: String?
    ): Result<GetPublicRoomsResponse> =
        httpClient.request(GetPublicRooms(limit = limit, server = server?.e(), since = since))

    override suspend fun getPublicRooms(
        limit: Long?,
        server: String?,
        since: String?,
        filter: GetPublicRoomsWithFilter.Request.Filter?,
        includeAllNetworks: Boolean?,
        thirdPartyInstanceId: String?,
        asUserId: UserId?
    ): Result<GetPublicRoomsResponse> =
        httpClient.request(
            GetPublicRoomsWithFilter(server?.e(), asUserId), GetPublicRoomsWithFilter.Request(
                limit = limit,
                since = since,
                filter = filter,
                includeAllNetworks = includeAllNetworks,
                thirdPartyInstanceId = thirdPartyInstanceId
            )
        )

    override suspend fun getTags(
        userId: UserId,
        roomId: RoomId,
        asUserId: UserId?
    ): Result<TagEventContent> =
        httpClient.request(GetRoomTags(userId.e(), roomId.e(), asUserId))

    override suspend fun setTag(
        userId: UserId,
        roomId: RoomId,
        tag: String,
        tagValue: TagEventContent.Tag,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(SetRoomTag(userId.e(), roomId.e(), tag.e(), asUserId), tagValue)

    override suspend fun deleteTag(
        userId: UserId,
        roomId: RoomId,
        tag: String,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(DeleteRoomTag(userId.e(), roomId.e(), tag.e(), asUserId))

    override suspend fun getEventContext(
        roomId: RoomId,
        eventId: EventId,
        filter: String?,
        limit: Long?,
        asUserId: UserId?
    ): Result<GetEventContext.Response> =
        httpClient.request(GetEventContext(roomId.e(), eventId.e(), filter?.e(), limit, asUserId))

    override suspend fun reportEvent(
        roomId: RoomId,
        eventId: EventId,
        reason: String?,
        score: Long?,
        asUserId: UserId?
    ): Result<Unit> =
        httpClient.request(ReportEvent(roomId.e(), eventId.e(), asUserId), ReportEvent.Request(reason, score))

    override suspend fun upgradeRoom(
        roomId: RoomId,
        version: String,
        asUserId: UserId?
    ): Result<RoomId> =
        httpClient.request(UpgradeRoom(roomId.e(), asUserId), UpgradeRoom.Request(version)).map { it.replacementRoom }

    override suspend fun getHierarchy(
        roomId: RoomId,
        from: String,
        limit: Long?,
        maxDepth: Long?,
        suggestedOnly: Boolean,
        asUserId: UserId?
    ): Result<GetHierarchy.Response> =
        httpClient.request(GetHierarchy(roomId.e(), from, limit, maxDepth, suggestedOnly, asUserId))
}

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridroomsroomidaccount_datatype">matrix spec</a>
 */
suspend inline fun <reified C : RoomAccountDataEventContent> IRoomsApiClient.getAccountData(
    roomId: RoomId,
    userId: UserId,
    key: String = "",
    asUserId: UserId? = null
): Result<C> {
    val type = contentMappings.roomAccountData.fromClass(C::class).type
    @Suppress("UNCHECKED_CAST")
    return getAccountData(type, roomId, userId, key, asUserId) as Result<C>
}

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidstateeventtypestatekey">matrix spec</a>
 */
suspend inline fun <reified C : StateEventContent> IRoomsApiClient.getStateEvent(
    roomId: RoomId,
    stateKey: String = "",
    asUserId: UserId? = null
): Result<C> {
    val type = contentMappings.state.fromClass(C::class).type
    @Suppress("UNCHECKED_CAST")
    return getStateEvent(type, roomId, stateKey, asUserId) as Result<C>
}