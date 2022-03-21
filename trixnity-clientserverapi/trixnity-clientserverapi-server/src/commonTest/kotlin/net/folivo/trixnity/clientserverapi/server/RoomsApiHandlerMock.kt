package net.folivo.trixnity.clientserverapi.server

import io.mockative.Invocation
import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.rooms.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent

class RoomsApiHandlerMock : io.mockative.Mockable(stubsUnitByDefault = false), RoomsApiHandler {
    override suspend fun banUser(context: MatrixEndpointContext<BanUser, BanUser.Request, Unit>): Unit = suspend(
        this, Invocation.Function("banUser", listOf<Any?>(context)), true
    )

    override suspend fun createRoom(context: MatrixEndpointContext<CreateRoom, CreateRoom.Request, CreateRoom.Response>): CreateRoom.Response =
        suspend(
            this, Invocation.Function("createRoom", listOf<Any?>(context)), false
        )

    override suspend fun deleteRoomAlias(context: MatrixEndpointContext<DeleteRoomAlias, Unit, Unit>): Unit = suspend(
        this, Invocation.Function("deleteRoomAlias", listOf<Any?>(context)), true
    )

    override suspend fun forgetRoom(context: MatrixEndpointContext<ForgetRoom, Unit, Unit>): Unit = suspend(
        this, Invocation.Function("forgetRoom", listOf<Any?>(context)), true
    )

    override suspend fun getAccountData(context: MatrixEndpointContext<GetRoomAccountData, Unit, RoomAccountDataEventContent>): RoomAccountDataEventContent =
        suspend(
            this, Invocation.Function("getAccountData", listOf<Any?>(context)), false
        )

    override suspend fun getEvent(context: MatrixEndpointContext<GetEvent, Unit, Event<*>>): Event<*> = suspend(
        this, Invocation.Function("getEvent", listOf<Any?>(context)), false
    )

    override suspend fun getEvents(context: MatrixEndpointContext<GetEvents, Unit, GetEvents.Response>): GetEvents.Response =
        suspend(
            this, Invocation.Function("getEvents", listOf<Any?>(context)), false
        )

    override suspend fun getJoinedMembers(context: MatrixEndpointContext<GetJoinedMembers, Unit, GetJoinedMembers.Response>): GetJoinedMembers.Response =
        suspend(
            this, Invocation.Function("getJoinedMembers", listOf<Any?>(context)), false
        )

    override suspend fun getJoinedRooms(context: MatrixEndpointContext<GetJoinedRooms, Unit, GetJoinedRooms.Response>): GetJoinedRooms.Response =
        suspend(
            this, Invocation.Function("getJoinedRooms", listOf<Any?>(context)), false
        )

    override suspend fun getMembers(context: MatrixEndpointContext<GetMembers, Unit, GetMembers.Response>): GetMembers.Response =
        suspend(
            this, Invocation.Function("getMembers", listOf<Any?>(context)), false
        )

    override suspend fun getRoomAlias(context: MatrixEndpointContext<GetRoomAlias, Unit, GetRoomAlias.Response>): GetRoomAlias.Response =
        suspend(
            this, Invocation.Function("getRoomAlias", listOf<Any?>(context)), false
        )

    override suspend fun getState(context: MatrixEndpointContext<GetState, Unit, List<Event.StateEvent<*>>>): List<Event.StateEvent<*>> =
        suspend(
            this, Invocation.Function("getState", listOf<Any?>(context)), false
        )

    override suspend fun getStateEvent(context: MatrixEndpointContext<GetStateEvent, Unit, StateEventContent>): StateEventContent =
        suspend(
            this, Invocation.Function("getStateEvent", listOf<Any?>(context)), false
        )

    override suspend fun inviteUser(context: MatrixEndpointContext<InviteUser, InviteUser.Request, Unit>): Unit =
        suspend(
            this, Invocation.Function("inviteUser", listOf<Any?>(context)), true
        )

    override suspend fun joinRoom(context: MatrixEndpointContext<JoinRoom, JoinRoom.Request, JoinRoom.Response>): JoinRoom.Response =
        suspend(
            this, Invocation.Function("joinRoom", listOf<Any?>(context)), false
        )

    override suspend fun kickUser(context: MatrixEndpointContext<KickUser, KickUser.Request, Unit>): Unit = suspend(
        this, Invocation.Function("kickUser", listOf<Any?>(context)), true
    )

    override suspend fun knockRoom(context: MatrixEndpointContext<KnockRoom, KnockRoom.Request, KnockRoom.Response>): KnockRoom.Response =
        suspend(
            this, Invocation.Function("knockRoom", listOf<Any?>(context)), false
        )

    override suspend fun leaveRoom(context: MatrixEndpointContext<LeaveRoom, LeaveRoom.Request, Unit>): Unit = suspend(
        this, Invocation.Function("leaveRoom", listOf<Any?>(context)), true
    )

    override suspend fun redactEvent(context: MatrixEndpointContext<RedactEvent, RedactEvent.Request, SendEventResponse>): SendEventResponse =
        suspend(
            this, Invocation.Function("redactEvent", listOf<Any?>(context)), false
        )

    override suspend fun sendMessageEvent(context: MatrixEndpointContext<SendMessageEvent, net.folivo.trixnity.core.model.events.MessageEventContent, SendEventResponse>): SendEventResponse =
        suspend(
            this, Invocation.Function("sendMessageEvent", listOf<Any?>(context)), false
        )

    override suspend fun sendStateEvent(context: MatrixEndpointContext<SendStateEvent, StateEventContent, SendEventResponse>): SendEventResponse =
        suspend(
            this, Invocation.Function("sendStateEvent", listOf<Any?>(context)), false
        )

    override suspend fun setAccountData(context: MatrixEndpointContext<SetRoomAccountData, RoomAccountDataEventContent, Unit>): Unit =
        suspend(
            this, Invocation.Function("setAccountData", listOf<Any?>(context)), true
        )

    override suspend fun setReadMarkers(context: MatrixEndpointContext<SetReadMarkers, SetReadMarkers.Request, Unit>): Unit =
        suspend(
            this, Invocation.Function("setReadMarkers", listOf<Any?>(context)), true
        )

    override suspend fun setReceipt(context: MatrixEndpointContext<SetReceipt, Unit, Unit>): Unit = suspend(
        this, Invocation.Function("setReceipt", listOf<Any?>(context)), true
    )

    override suspend fun setRoomAlias(context: MatrixEndpointContext<SetRoomAlias, SetRoomAlias.Request, Unit>): Unit =
        suspend(
            this, Invocation.Function("setRoomAlias", listOf<Any?>(context)), true
        )

    override suspend fun setTyping(context: MatrixEndpointContext<SetTyping, SetTyping.Request, Unit>): Unit = suspend(
        this, Invocation.Function("setTyping", listOf<Any?>(context)), true
    )

    override suspend fun unbanUser(context: MatrixEndpointContext<UnbanUser, UnbanUser.Request, Unit>): Unit = suspend(
        this, Invocation.Function("unbanUser", listOf<Any?>(context)), true
    )
}