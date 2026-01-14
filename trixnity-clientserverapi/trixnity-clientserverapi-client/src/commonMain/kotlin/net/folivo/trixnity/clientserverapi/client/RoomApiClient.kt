package net.folivo.trixnity.clientserverapi.client

import net.folivo.trixnity.clientserverapi.model.rooms.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.*
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.ReceiptType
import net.folivo.trixnity.core.model.events.m.RelationType
import net.folivo.trixnity.core.model.events.m.TagEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.contentType
import net.folivo.trixnity.utils.nextString
import kotlin.random.Random

interface RoomApiClient {

    val contentMappings: EventContentSerializerMappings

    /**
     * @see [GetEvent]
     */
    suspend fun getEvent(
        roomId: RoomId,
        eventId: EventId,
    ): Result<RoomEvent<*>>

    /**
     * @see [GetStateEvent]
     */
    suspend fun getStateEvent(
        type: String,
        roomId: RoomId,
        stateKey: String = "",
    ): Result<ClientEvent.StateBaseEvent<*>>

    /**
     * @see [GetStateEventContent]
     */
    suspend fun getStateEventContent(
        type: String,
        roomId: RoomId,
        stateKey: String = "",
    ): Result<StateEventContent>

    /**
     * @see [GetState]
     */
    suspend fun getState(roomId: RoomId): Result<List<StateEvent<*>>>

    /**
     * @see [GetMembers]
     */
    suspend fun getMembers(
        roomId: RoomId,
        at: String? = null,
        membership: Membership? = null,
        notMembership: Membership? = null,
    ): Result<Set<StateEvent<MemberEventContent>>>

    /**
     * @see [GetJoinedMembers]
     */
    suspend fun getJoinedMembers(
        roomId: RoomId,
    ): Result<GetJoinedMembers.Response>

    /**
     * @see [GetEvents]
     */
    suspend fun getEvents(
        roomId: RoomId,
        from: String,
        dir: GetEvents.Direction = GetEvents.Direction.FORWARDS,
        to: String? = null,
        limit: Long? = null,
        filter: String? = null,
    ): Result<GetEvents.Response>

    /**
     * @see [GetRelations]
     */
    suspend fun getRelations(
        roomId: RoomId,
        eventId: EventId,
        from: String? = null,
        to: String? = null,
        limit: Long? = null,
        recurse: Boolean? = null,
    ): Result<GetRelationsResponse>

    /**
     * @see [GetRelationsByRelationType]
     */
    suspend fun getRelations(
        roomId: RoomId,
        eventId: EventId,
        relationType: RelationType,
        from: String? = null,
        to: String? = null,
        limit: Long? = null,
        recurse: Boolean? = null,
    ): Result<GetRelationsResponse>

    /**
     * @see [GetRelationsByRelationTypeAndEventType]
     */
    suspend fun getRelations(
        roomId: RoomId,
        eventId: EventId,
        relationType: RelationType,
        eventType: String,
        from: String? = null,
        to: String? = null,
        limit: Long? = null,
    ): Result<GetRelationsResponse>

    /**
     * @see [GetThreads]
     */
    suspend fun getThreads(
        roomId: RoomId,
        from: String? = null,
        include: GetThreads.Include? = null,
        limit: Long? = null,
    ): Result<GetThreads.Response>

    /**
     * @see [SendStateEvent]
     */
    suspend fun sendStateEvent(
        roomId: RoomId,
        eventContent: StateEventContent,
        stateKey: String = "",
    ): Result<EventId>

    /**
     * @see [SendMessageEvent]
     */
    suspend fun sendMessageEvent(
        roomId: RoomId,
        eventContent: MessageEventContent,
        txnId: String = Random.nextString(22),
    ): Result<EventId>

    /**
     * @see [RedactEvent]
     */
    suspend fun redactEvent(
        roomId: RoomId,
        eventId: EventId,
        reason: String? = null,
        txnId: String = Random.nextString(22),
    ): Result<EventId>

    /**
     * @see [CreateRoom]
     */
    suspend fun createRoom(
        visibility: DirectoryVisibility = DirectoryVisibility.PRIVATE,
        roomAliasId: RoomAliasId? = null,
        name: String? = null,
        topic: String? = null,
        invite: Set<UserId>? = null,
        inviteThirdPid: Set<CreateRoom.Request.InviteThirdPid>? = null,
        roomVersion: String? = null,
        creationContent: CreateEventContent? = null,
        initialState: List<InitialStateEvent<*>>? = null,
        preset: CreateRoom.Request.Preset? = null,
        isDirect: Boolean? = null,
        powerLevelContentOverride: PowerLevelsEventContent? = null,
    ): Result<RoomId>

    /**
     * @see [SetRoomAlias]
     */
    suspend fun setRoomAlias(
        roomId: RoomId,
        roomAliasId: RoomAliasId,
    ): Result<Unit>

    /**
     * @see [GetRoomAlias]
     */
    suspend fun getRoomAlias(
        roomAliasId: RoomAliasId,
    ): Result<GetRoomAlias.Response>

    /**
     * @see [GetRoomAliases]
     */
    suspend fun getRoomAliases(
        roomId: RoomId,
    ): Result<Set<RoomAliasId>>

    /**
     * @see [DeleteRoomAlias]
     */
    suspend fun deleteRoomAlias(
        roomAliasId: RoomAliasId,
    ): Result<Unit>

    /**
     * @see [GetJoinedRooms]
     */
    suspend fun getJoinedRooms(): Result<Set<RoomId>>

    /**
     * @see [InviteUser]
     */
    suspend fun inviteUser(
        roomId: RoomId,
        userId: UserId,
        reason: String? = null,
    ): Result<Unit>

    /**
     * @see [KickUser]
     */
    suspend fun kickUser(
        roomId: RoomId,
        userId: UserId,
        reason: String? = null,
    ): Result<Unit>

    /**
     * @see [BanUser]
     */
    suspend fun banUser(
        roomId: RoomId,
        userId: UserId,
        reason: String? = null,
    ): Result<Unit>

    /**
     * @see [UnbanUser]
     */
    suspend fun unbanUser(
        roomId: RoomId,
        userId: UserId,
        reason: String? = null,
    ): Result<Unit>

    /**
     * @see [JoinRoom]
     */
    suspend fun joinRoom(
        roomId: RoomId,
        via: Set<String>? = null,
        reason: String? = null,
        thirdPartySigned: Signed<JoinRoom.Request.ThirdParty, String>? = null,
    ): Result<RoomId>

    /**
     * @see [JoinRoom]
     */
    suspend fun joinRoom(
        roomAliasId: RoomAliasId,
        via: Set<String>? = null,
        reason: String? = null,
        thirdPartySigned: Signed<JoinRoom.Request.ThirdParty, String>? = null,
    ): Result<RoomId>

    /**
     * @see [KnockRoom]
     */
    suspend fun knockRoom(
        roomId: RoomId,
        via: Set<String>? = null,
        reason: String? = null,
    ): Result<RoomId>

    /**
     * @see [KnockRoom]
     */
    suspend fun knockRoom(
        roomAliasId: RoomAliasId,
        via: Set<String>? = null,
        reason: String? = null,
    ): Result<RoomId>

    /**
     * @see [ForgetRoom]
     */
    suspend fun forgetRoom(
        roomId: RoomId,
    ): Result<Unit>

    /**
     * @see [LeaveRoom]
     */
    suspend fun leaveRoom(
        roomId: RoomId,
        reason: String? = null,
    ): Result<Unit>

    /**
     * @see [SetReceipt]
     */
    suspend fun setReceipt(
        roomId: RoomId,
        eventId: EventId,
        receiptType: ReceiptType = ReceiptType.Read,
        threadId: EventId? = null,
    ): Result<Unit>

    /**
     * @see [SetReadMarkers]
     */
    suspend fun setReadMarkers(
        roomId: RoomId,
        fullyRead: EventId? = null,
        read: EventId? = null,
        privateRead: EventId? = null,
    ): Result<Unit>

    /**
     * @see [SetTyping]
     */
    suspend fun setTyping(
        roomId: RoomId,
        userId: UserId,
        typing: Boolean,
        timeout: Long? = null,
    ): Result<Unit>

    /**
     * @see [GetRoomAccountData]
     */
    suspend fun getAccountData(
        type: String,
        roomId: RoomId,
        userId: UserId,
        key: String = "",
    ): Result<RoomAccountDataEventContent>

    /**
     * @see [SetRoomAccountData]
     */
    suspend fun setAccountData(
        content: RoomAccountDataEventContent,
        roomId: RoomId,
        userId: UserId,
        key: String = "",
    ): Result<Unit>

    /**
     * @see [GetDirectoryVisibility]
     */
    suspend fun getDirectoryVisibility(
        roomId: RoomId,
    ): Result<DirectoryVisibility>

    /**
     * @see [SetDirectoryVisibility]
     */
    suspend fun setDirectoryVisibility(
        roomId: RoomId,
        visibility: DirectoryVisibility,
    ): Result<Unit>

    /**
     * @see [GetPublicRooms]
     */
    suspend fun getPublicRooms(
        limit: Long? = null,
        server: String? = null,
        since: String? = null
    ): Result<GetPublicRoomsResponse>

    /**
     * @see [GetPublicRoomsWithFilter]
     */
    suspend fun getPublicRooms(
        limit: Long? = null,
        server: String? = null,
        since: String? = null,
        filter: GetPublicRoomsWithFilter.Request.Filter? = null,
        includeAllNetworks: Boolean? = null,
        thirdPartyInstanceId: String? = null,
    ): Result<GetPublicRoomsResponse>

    /**
     * @see [GetRoomTags]
     */
    suspend fun getTags(
        userId: UserId,
        roomId: RoomId,
    ): Result<TagEventContent>

    /**
     * @see [SetRoomTag]
     */
    suspend fun setTag(
        userId: UserId,
        roomId: RoomId,
        tag: String,
        tagValue: TagEventContent.Tag,
    ): Result<Unit>

    /**
     * @see [DeleteRoomTag]
     */
    suspend fun deleteTag(
        userId: UserId,
        roomId: RoomId,
        tag: String,
    ): Result<Unit>

    /**
     * @see [GetEventContext]
     */
    suspend fun getEventContext(
        roomId: RoomId,
        eventId: EventId,
        filter: String? = null,
        limit: Long? = null,
    ): Result<GetEventContext.Response>

    /**
     * @see [ReportRoom]
     */
    suspend fun reportRoom(
        roomId: RoomId,
        reason: String,
    ): Result<Unit>

    /**
     * @see [ReportEvent]
     */
    suspend fun reportEvent(
        roomId: RoomId,
        eventId: EventId,
        reason: String? = null,
        score: Long? = null,
    ): Result<Unit>

    /**
     * @see [UpgradeRoom]
     */
    suspend fun upgradeRoom(
        roomId: RoomId,
        version: String,
        additionalCreators: Set<UserId>? = null,
    ): Result<RoomId>

    /**
     * @see [GetHierarchy]
     */
    suspend fun getHierarchy(
        roomId: RoomId,
        from: String? = null,
        limit: Long? = null,
        maxDepth: Long? = null,
        suggestedOnly: Boolean = false,
    ): Result<GetHierarchy.Response>

    /**
     * @see [TimestampToEvent]
     */
    suspend fun timestampToEvent(
        roomId: RoomId,
        timestamp: Long,
        dir: TimestampToEvent.Direction = TimestampToEvent.Direction.FORWARDS,
    ): Result<TimestampToEvent.Response>

    /**
     * @see [GetSummary]
     */
    suspend fun getSummary(
        roomAliasId: RoomAliasId,
        via: Set<String>? = null,
    ): Result<GetSummary.Response>

    /**
     * @see [GetSummary]
     */
    suspend fun getSummary(
        roomId: RoomId,
        via: Set<String>? = null,
    ): Result<GetSummary.Response>
}

class RoomApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient,
    override val contentMappings: EventContentSerializerMappings
) : RoomApiClient {

    override suspend fun getEvent(
        roomId: RoomId,
        eventId: EventId,
    ): Result<RoomEvent<*>> =
        baseClient.request(GetEvent(roomId, eventId))

    override suspend fun getStateEvent(
        type: String,
        roomId: RoomId,
        stateKey: String,
    ): Result<ClientEvent.StateBaseEvent<*>> =
        baseClient.request(GetStateEvent(roomId, type, stateKey, GetStateEvent.Format.EVENT))
            .mapCatching {
                check(it is GetStateEvent.Response.Event)
                it.value
            }

    override suspend fun getStateEventContent(
        type: String,
        roomId: RoomId,
        stateKey: String,
    ): Result<StateEventContent> =
        baseClient.request(GetStateEvent(roomId, type, stateKey, GetStateEvent.Format.CONTENT))
            .mapCatching {
                check(it is GetStateEvent.Response.Content)
                it.value
            }

    override suspend fun getState(roomId: RoomId): Result<List<StateEvent<*>>> =
        baseClient.request(GetState(roomId))

    override suspend fun getMembers(
        roomId: RoomId,
        at: String?,
        membership: Membership?,
        notMembership: Membership?,
    ): Result<Set<StateEvent<MemberEventContent>>> =
        baseClient.request(GetMembers(roomId, at, membership, notMembership))
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
    ): Result<GetJoinedMembers.Response> =
        baseClient.request(GetJoinedMembers(roomId))

    override suspend fun getEvents(
        roomId: RoomId,
        from: String,
        dir: GetEvents.Direction,
        to: String?,
        limit: Long?,
        filter: String?,
    ): Result<GetEvents.Response> =
        baseClient.request(GetEvents(roomId, from, to, dir, limit, filter))

    override suspend fun getRelations(
        roomId: RoomId,
        eventId: EventId,
        from: String?,
        to: String?,
        limit: Long?,
        recurse: Boolean?,
    ): Result<GetRelationsResponse> =
        baseClient.request(GetRelations(roomId, eventId, from, to, limit, recurse))

    override suspend fun getRelations(
        roomId: RoomId,
        eventId: EventId,
        relationType: RelationType,
        from: String?,
        to: String?,
        limit: Long?,
        recurse: Boolean?,
    ): Result<GetRelationsResponse> =
        baseClient.request(
            GetRelationsByRelationType(
                roomId,
                eventId,
                relationType,
                from,
                to,
                limit,
                recurse,
            )
        )

    override suspend fun getRelations(
        roomId: RoomId,
        eventId: EventId,
        relationType: RelationType,
        eventType: String,
        from: String?,
        to: String?,
        limit: Long?,
    ): Result<GetRelationsResponse> =
        baseClient.request(
            GetRelationsByRelationTypeAndEventType(
                roomId,
                eventId,
                relationType,
                eventType,
                from,
                to,
                limit,
            )
        )

    override suspend fun getThreads(
        roomId: RoomId,
        from: String?,
        include: GetThreads.Include?,
        limit: Long?,
    ): Result<GetThreads.Response> =
        baseClient.request(GetThreads(roomId, from, include, limit))

    override suspend fun sendStateEvent(
        roomId: RoomId,
        eventContent: StateEventContent,
        stateKey: String,
    ): Result<EventId> {
        val eventType = contentMappings.state.contentType(eventContent)
        return baseClient.request(SendStateEvent(roomId, eventType, stateKey), eventContent)
            .mapCatching { it.eventId }
    }

    override suspend fun sendMessageEvent(
        roomId: RoomId,
        eventContent: MessageEventContent,
        txnId: String,
    ): Result<EventId> {
        val eventType = contentMappings.message.contentType(eventContent)
        return baseClient.request(SendMessageEvent(roomId, eventType, txnId), eventContent)
            .mapCatching { it.eventId }
    }

    override suspend fun redactEvent(
        roomId: RoomId,
        eventId: EventId,
        reason: String?,
        txnId: String,
    ): Result<EventId> =
        baseClient.request(RedactEvent(roomId, eventId, txnId), RedactEvent.Request(reason))
            .mapCatching { it.eventId }

    override suspend fun createRoom(
        visibility: DirectoryVisibility,
        roomAliasId: RoomAliasId?,
        name: String?,
        topic: String?,
        invite: Set<UserId>?,
        inviteThirdPid: Set<CreateRoom.Request.InviteThirdPid>?,
        roomVersion: String?,
        creationContent: CreateEventContent?,
        initialState: List<InitialStateEvent<*>>?,
        preset: CreateRoom.Request.Preset?,
        isDirect: Boolean?,
        powerLevelContentOverride: PowerLevelsEventContent?,
    ): Result<RoomId> =
        baseClient.request(
            CreateRoom,
            CreateRoom.Request(
                visibility = visibility,
                roomAliasLocalPart = roomAliasId?.localpart,
                name = name,
                topic = topic,
                invite = invite,
                inviteThirdPid = inviteThirdPid,
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
    ): Result<Unit> =
        baseClient.request(SetRoomAlias(roomAliasId), SetRoomAlias.Request(roomId))

    override suspend fun getRoomAlias(
        roomAliasId: RoomAliasId,
    ): Result<GetRoomAlias.Response> =
        baseClient.request(GetRoomAlias(roomAliasId))

    override suspend fun getRoomAliases(
        roomId: RoomId,
    ): Result<Set<RoomAliasId>> =
        baseClient.request(GetRoomAliases(roomId)).map { it.aliases }

    override suspend fun deleteRoomAlias(
        roomAliasId: RoomAliasId,
    ): Result<Unit> =
        baseClient.request(DeleteRoomAlias(roomAliasId))

    override suspend fun getJoinedRooms(): Result<Set<RoomId>> =
        baseClient.request(GetJoinedRooms).mapCatching { it.joinedRooms }

    override suspend fun inviteUser(
        roomId: RoomId,
        userId: UserId,
        reason: String?,
    ): Result<Unit> =
        baseClient.request(InviteUser(roomId), InviteUser.Request(userId, reason))

    override suspend fun kickUser(
        roomId: RoomId,
        userId: UserId,
        reason: String?,
    ): Result<Unit> =
        baseClient.request(KickUser(roomId), KickUser.Request(userId, reason))

    override suspend fun banUser(
        roomId: RoomId,
        userId: UserId,
        reason: String?,
    ): Result<Unit> =
        baseClient.request(BanUser(roomId), BanUser.Request(userId, reason))


    override suspend fun unbanUser(
        roomId: RoomId,
        userId: UserId,
        reason: String?,
    ): Result<Unit> =
        baseClient.request(UnbanUser(roomId), UnbanUser.Request(userId, reason))


    override suspend fun joinRoom(
        roomId: RoomId,
        via: Set<String>?,
        reason: String?,
        thirdPartySigned: Signed<JoinRoom.Request.ThirdParty, String>?,
    ): Result<RoomId> =
        baseClient.request(JoinRoom(roomId.full, via), JoinRoom.Request(reason, thirdPartySigned))
            .mapCatching { it.roomId }

    override suspend fun joinRoom(
        roomAliasId: RoomAliasId,
        via: Set<String>?,
        reason: String?,
        thirdPartySigned: Signed<JoinRoom.Request.ThirdParty, String>?,
    ): Result<RoomId> =
        baseClient.request(
            JoinRoom(roomAliasId.full, via),
            JoinRoom.Request(reason, thirdPartySigned)
        ).mapCatching { it.roomId }

    override suspend fun knockRoom(
        roomId: RoomId,
        via: Set<String>?,
        reason: String?,
    ): Result<RoomId> =
        baseClient.request(KnockRoom(roomId.full, via), KnockRoom.Request(reason))
            .mapCatching { it.roomId }

    override suspend fun knockRoom(
        roomAliasId: RoomAliasId,
        via: Set<String>?,
        reason: String?,
    ): Result<RoomId> =
        baseClient.request(KnockRoom(roomAliasId.full, via), KnockRoom.Request(reason))
            .mapCatching { it.roomId }

    override suspend fun forgetRoom(
        roomId: RoomId,
    ): Result<Unit> =
        baseClient.request(ForgetRoom(roomId))

    override suspend fun leaveRoom(
        roomId: RoomId,
        reason: String?,
    ): Result<Unit> =
        baseClient.request(LeaveRoom(roomId), LeaveRoom.Request(reason))

    override suspend fun setReceipt(
        roomId: RoomId,
        eventId: EventId,
        receiptType: ReceiptType,
        threadId: EventId?,
    ): Result<Unit> =
        baseClient.request(SetReceipt(roomId, receiptType, eventId), SetReceipt.Request(threadId))

    override suspend fun setReadMarkers(
        roomId: RoomId,
        fullyRead: EventId?,
        read: EventId?,
        privateRead: EventId?,
    ): Result<Unit> =
        baseClient.request(SetReadMarkers(roomId), SetReadMarkers.Request(fullyRead, read, privateRead))

    override suspend fun setTyping(
        roomId: RoomId,
        userId: UserId,
        typing: Boolean,
        timeout: Long?,
    ): Result<Unit> =
        baseClient.request(SetTyping(roomId, userId), SetTyping.Request(typing, timeout))

    override suspend fun getAccountData(
        type: String,
        roomId: RoomId,
        userId: UserId,
        key: String,
    ): Result<RoomAccountDataEventContent> {
        val actualType = if (key.isEmpty()) type else type + key
        return baseClient.request(GetRoomAccountData(userId, roomId, actualType))
    }

    override suspend fun setAccountData(
        content: RoomAccountDataEventContent,
        roomId: RoomId,
        userId: UserId,
        key: String,
    ): Result<Unit> {
        val eventType = contentMappings.roomAccountData.contentType(content)
            .let { type -> if (key.isEmpty()) type else type + key }

        return baseClient.request(SetRoomAccountData(userId, roomId, eventType), content)
    }

    override suspend fun getDirectoryVisibility(
        roomId: RoomId,
    ): Result<DirectoryVisibility> =
        baseClient.request(GetDirectoryVisibility(roomId)).map { it.visibility }

    override suspend fun setDirectoryVisibility(
        roomId: RoomId,
        visibility: DirectoryVisibility,
    ): Result<Unit> =
        baseClient.request(SetDirectoryVisibility(roomId), SetDirectoryVisibility.Request(visibility))

    override suspend fun getPublicRooms(
        limit: Long?,
        server: String?,
        since: String?
    ): Result<GetPublicRoomsResponse> =
        baseClient.request(GetPublicRooms(limit = limit, server = server, since = since))

    override suspend fun getPublicRooms(
        limit: Long?,
        server: String?,
        since: String?,
        filter: GetPublicRoomsWithFilter.Request.Filter?,
        includeAllNetworks: Boolean?,
        thirdPartyInstanceId: String?,
    ): Result<GetPublicRoomsResponse> =
        baseClient.request(
            GetPublicRoomsWithFilter(server), GetPublicRoomsWithFilter.Request(
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
    ): Result<TagEventContent> =
        baseClient.request(GetRoomTags(userId, roomId))

    override suspend fun setTag(
        userId: UserId,
        roomId: RoomId,
        tag: String,
        tagValue: TagEventContent.Tag,
    ): Result<Unit> =
        baseClient.request(SetRoomTag(userId, roomId, tag), tagValue)

    override suspend fun deleteTag(
        userId: UserId,
        roomId: RoomId,
        tag: String,
    ): Result<Unit> =
        baseClient.request(DeleteRoomTag(userId, roomId, tag))

    override suspend fun getEventContext(
        roomId: RoomId,
        eventId: EventId,
        filter: String?,
        limit: Long?,
    ): Result<GetEventContext.Response> =
        baseClient.request(GetEventContext(roomId, eventId, filter, limit))

    override suspend fun reportRoom(
        roomId: RoomId,
        reason: String,
    ): Result<Unit> =
        baseClient.request(ReportRoom(roomId), ReportRoom.Request(reason))

    override suspend fun reportEvent(
        roomId: RoomId,
        eventId: EventId,
        reason: String?,
        score: Long?,
    ): Result<Unit> =
        baseClient.request(ReportEvent(roomId, eventId), ReportEvent.Request(reason, score))

    override suspend fun upgradeRoom(
        roomId: RoomId,
        version: String,
        additionalCreators: Set<UserId>?,
    ): Result<RoomId> =
        baseClient.request(UpgradeRoom(roomId, additionalCreators), UpgradeRoom.Request(version))
            .map { it.replacementRoom }

    override suspend fun getHierarchy(
        roomId: RoomId,
        from: String?,
        limit: Long?,
        maxDepth: Long?,
        suggestedOnly: Boolean,
    ): Result<GetHierarchy.Response> =
        baseClient.request(GetHierarchy(roomId, from, limit, maxDepth, suggestedOnly))

    override suspend fun timestampToEvent(
        roomId: RoomId,
        timestamp: Long,
        dir: TimestampToEvent.Direction
    ): Result<TimestampToEvent.Response> =
        baseClient.request(TimestampToEvent(roomId, timestamp, dir))

    override suspend fun getSummary(
        roomAliasId: RoomAliasId,
        via: Set<String>?,
    ): Result<GetSummary.Response> =
        baseClient.request(GetSummary(roomAliasId.full, via))

    override suspend fun getSummary(
        roomId: RoomId,
        via: Set<String>?,
    ): Result<GetSummary.Response> =
        baseClient.request(GetSummary(roomId.full, via))
}

/**
 * @see [GetRoomAccountData]
 */
suspend inline fun <reified C : RoomAccountDataEventContent> RoomApiClient.getAccountData(
    roomId: RoomId,
    userId: UserId,
    key: String = "",
): Result<C> {
    val type = contentMappings.roomAccountData.contentType(C::class)
    @Suppress("UNCHECKED_CAST")
    return getAccountData(type, roomId, userId, key) as Result<C>
}

/**
 * @see [GetStateEvent]
 */
suspend inline fun <reified C : StateEventContent> RoomApiClient.getStateEvent(
    roomId: RoomId,
    stateKey: String = "",
): Result<ClientEvent.StateBaseEvent<C>> {
    val type = contentMappings.state.contentType(C::class)
    @Suppress("UNCHECKED_CAST")
    return getStateEvent(type, roomId, stateKey) as Result<ClientEvent.StateBaseEvent<C>>
}

/**
 * @see [GetStateEventContent]
 */
suspend inline fun <reified C : StateEventContent> RoomApiClient.getStateEventContent(
    roomId: RoomId,
    stateKey: String = "",
): Result<C> {
    val type = contentMappings.state.contentType(C::class)
    @Suppress("UNCHECKED_CAST")
    return getStateEventContent(type, roomId, stateKey) as Result<C>
}

/**
 * @see [GetRelationsByRelationTypeAndEventType]
 */
suspend inline fun <reified C : MessageEventContent> RoomApiClient.getRelationsByType(
    roomId: RoomId,
    eventId: EventId,
    relationType: RelationType,
    from: String? = null,
    to: String? = null,
    limit: Long? = null,
): Result<GetRelationsResponse> {
    val eventType = contentMappings.message.contentType(C::class)
    return getRelations(roomId, eventId, relationType, eventType, from, to, limit)
}
