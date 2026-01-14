package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.rooms.*
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.TagEventContent

interface RoomsApiHandler {
    /**
     * @see [GetEvent]
     */
    suspend fun getEvent(context: MatrixEndpointContext<GetEvent, Unit, RoomEvent<*>>): RoomEvent<*>

    /**
     * @see [GetStateEvent]
     */
    suspend fun getStateEvent(context: MatrixEndpointContext<GetStateEvent, Unit, GetStateEvent.Response>): GetStateEvent.Response

    /**
     * @see [GetState]
     */
    suspend fun getState(context: MatrixEndpointContext<GetState, Unit, List<StateEvent<*>>>): List<StateEvent<*>>

    /**
     * @see [GetMembers]
     */
    suspend fun getMembers(context: MatrixEndpointContext<GetMembers, Unit, GetMembers.Response>): GetMembers.Response

    /**
     * @see [GetJoinedMembers]
     */
    suspend fun getJoinedMembers(context: MatrixEndpointContext<GetJoinedMembers, Unit, GetJoinedMembers.Response>): GetJoinedMembers.Response

    /**
     * @see [GetEvents]
     */
    suspend fun getEvents(context: MatrixEndpointContext<GetEvents, Unit, GetEvents.Response>): GetEvents.Response

    /**
     * @see [GetRelations]
     */
    suspend fun getRelations(context: MatrixEndpointContext<GetRelations, Unit, GetRelationsResponse>): GetRelationsResponse

    /**
     * @see [GetRelationsByRelationType]
     */
    suspend fun getRelationsByRelationType(context: MatrixEndpointContext<GetRelationsByRelationType, Unit, GetRelationsResponse>): GetRelationsResponse

    /**
     * @see [GetRelationsByRelationTypeAndEventType]
     */
    suspend fun getRelationsByRelationTypeAndEventType(context: MatrixEndpointContext<GetRelationsByRelationTypeAndEventType, Unit, GetRelationsResponse>): GetRelationsResponse

    /**
     * @see [GetRelationsByRelationTypeAndEventType]
     */
    suspend fun getThreads(context: MatrixEndpointContext<GetThreads, Unit, GetThreads.Response>): GetThreads.Response

    /**
     * @see [SendStateEvent]
     */
    suspend fun sendStateEvent(context: MatrixEndpointContext<SendStateEvent, StateEventContent, SendEventResponse>): SendEventResponse

    /**
     * @see [SendMessageEvent]
     */
    suspend fun sendMessageEvent(context: MatrixEndpointContext<SendMessageEvent, MessageEventContent, SendEventResponse>): SendEventResponse

    /**
     * @see [RedactEvent]
     */
    suspend fun redactEvent(context: MatrixEndpointContext<RedactEvent, RedactEvent.Request, SendEventResponse>): SendEventResponse

    /**
     * @see [CreateRoom]
     */
    suspend fun createRoom(context: MatrixEndpointContext<CreateRoom, CreateRoom.Request, CreateRoom.Response>): CreateRoom.Response

    /**
     * @see [SetRoomAlias]
     */
    suspend fun setRoomAlias(context: MatrixEndpointContext<SetRoomAlias, SetRoomAlias.Request, Unit>)

    /**
     * @see [GetRoomAlias]
     */
    suspend fun getRoomAlias(context: MatrixEndpointContext<GetRoomAlias, Unit, GetRoomAlias.Response>): GetRoomAlias.Response

    /**
     * @see [GetRoomAliases]
     */
    suspend fun getRoomAliases(context: MatrixEndpointContext<GetRoomAliases, Unit, GetRoomAliases.Response>): GetRoomAliases.Response

    /**
     * @see [DeleteRoomAlias]
     */
    suspend fun deleteRoomAlias(context: MatrixEndpointContext<DeleteRoomAlias, Unit, Unit>)

    /**
     * @see [GetJoinedRooms]
     */
    suspend fun getJoinedRooms(context: MatrixEndpointContext<GetJoinedRooms, Unit, GetJoinedRooms.Response>): GetJoinedRooms.Response

    /**
     * @see [InviteUser]
     */
    suspend fun inviteUser(context: MatrixEndpointContext<InviteUser, InviteUser.Request, Unit>)

    /**
     * @see [KickUser]
     */
    suspend fun kickUser(context: MatrixEndpointContext<KickUser, KickUser.Request, Unit>)

    /**
     * @see [BanUser]
     */
    suspend fun banUser(context: MatrixEndpointContext<BanUser, BanUser.Request, Unit>)

    /**
     * @see [UnbanUser]
     */
    suspend fun unbanUser(context: MatrixEndpointContext<UnbanUser, UnbanUser.Request, Unit>)

    /**
     * @see [JoinRoom]
     */
    suspend fun joinRoom(context: MatrixEndpointContext<JoinRoom, JoinRoom.Request, JoinRoom.Response>): JoinRoom.Response

    /**
     * @see [KnockRoom]
     */
    suspend fun knockRoom(context: MatrixEndpointContext<KnockRoom, KnockRoom.Request, KnockRoom.Response>): KnockRoom.Response

    /**
     * @see [ForgetRoom]
     */
    suspend fun forgetRoom(context: MatrixEndpointContext<ForgetRoom, Unit, Unit>)

    /**
     * @see [LeaveRoom]
     */
    suspend fun leaveRoom(context: MatrixEndpointContext<LeaveRoom, LeaveRoom.Request, Unit>)

    /**
     * @see [SetReceipt]
     */
    suspend fun setReceipt(context: MatrixEndpointContext<SetReceipt, SetReceipt.Request, Unit>)

    /**
     * @see [SetReadMarkers]
     */
    suspend fun setReadMarkers(context: MatrixEndpointContext<SetReadMarkers, SetReadMarkers.Request, Unit>)

    /**
     * @see [SetTyping]
     */
    suspend fun setTyping(context: MatrixEndpointContext<SetTyping, SetTyping.Request, Unit>)

    /**
     * @see [GetRoomAccountData]
     */
    suspend fun getAccountData(context: MatrixEndpointContext<GetRoomAccountData, Unit, RoomAccountDataEventContent>): RoomAccountDataEventContent

    /**
     * @see [SetRoomAccountData]
     */
    suspend fun setAccountData(context: MatrixEndpointContext<SetRoomAccountData, RoomAccountDataEventContent, Unit>)

    /**
     * @see [GetDirectoryVisibility]
     */
    suspend fun getDirectoryVisibility(context: MatrixEndpointContext<GetDirectoryVisibility, Unit, GetDirectoryVisibility.Response>): GetDirectoryVisibility.Response

    /**
     * @see [SetDirectoryVisibility]
     */
    suspend fun setDirectoryVisibility(context: MatrixEndpointContext<SetDirectoryVisibility, SetDirectoryVisibility.Request, Unit>)

    /**
     * @see [GetPublicRooms]
     */
    suspend fun getPublicRooms(context: MatrixEndpointContext<GetPublicRooms, Unit, GetPublicRoomsResponse>): GetPublicRoomsResponse

    /**
     * @see [GetPublicRoomsWithFilter]
     */
    suspend fun getPublicRoomsWithFilter(context: MatrixEndpointContext<GetPublicRoomsWithFilter, GetPublicRoomsWithFilter.Request, GetPublicRoomsResponse>): GetPublicRoomsResponse

    /**
     * @see [GetRoomTags]
     */
    suspend fun getTags(context: MatrixEndpointContext<GetRoomTags, Unit, TagEventContent>): TagEventContent

    /**
     * @see [SetRoomTag]
     */
    suspend fun setTag(context: MatrixEndpointContext<SetRoomTag, TagEventContent.Tag, Unit>)

    /**
     * @see [DeleteRoomTag]
     */
    suspend fun deleteTag(context: MatrixEndpointContext<DeleteRoomTag, Unit, Unit>)

    /**
     * @see [GetEventContext]
     */
    suspend fun getEventContext(context: MatrixEndpointContext<GetEventContext, Unit, GetEventContext.Response>): GetEventContext.Response

    /**
     * @see [ReportRoom]
     */
    suspend fun reportRoom(context: MatrixEndpointContext<ReportRoom, ReportRoom.Request, Unit>)

    /**
     * @see [ReportEvent]
     */
    suspend fun reportEvent(context: MatrixEndpointContext<ReportEvent, ReportEvent.Request, Unit>)

    /**
     * @see [UpgradeRoom]
     */
    suspend fun upgradeRoom(context: MatrixEndpointContext<UpgradeRoom, UpgradeRoom.Request, UpgradeRoom.Response>): UpgradeRoom.Response

    /**
     * @see [GetHierarchy]
     */
    suspend fun getHierarchy(context: MatrixEndpointContext<GetHierarchy, Unit, GetHierarchy.Response>): GetHierarchy.Response

    /**
     * @see [TimestampToEvent]
     */
    suspend fun timestampToEvent(context: MatrixEndpointContext<TimestampToEvent, Unit, TimestampToEvent.Response>): TimestampToEvent.Response

    /**
     * @see [GetSummary]
     */
    suspend fun getSummary(context: MatrixEndpointContext<GetSummary, Unit, GetSummary.Response>): GetSummary.Response
}