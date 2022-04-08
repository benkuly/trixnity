package net.folivo.trixnity.clientserverapi.server

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.server.matrixEndpoint
import net.folivo.trixnity.clientserverapi.model.rooms.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.TagEventContent
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

internal fun Route.roomsApiRoutes(
    handler: RoomsApiHandler,
    json: Json,
    contentMappings: EventContentSerializerMappings
) {
    authenticate {
        matrixEndpoint<GetEvent, Event<*>>(json, contentMappings) {
            handler.getEvent(this)
        }
        matrixEndpoint<GetStateEvent, StateEventContent>(json, contentMappings) {
            handler.getStateEvent(this)
        }
        matrixEndpoint<GetState, List<Event.StateEvent<*>>>(json, contentMappings) {
            handler.getState(this)
        }
        matrixEndpoint<GetMembers, GetMembers.Response>(json, contentMappings) {
            handler.getMembers(this)
        }
        matrixEndpoint<GetJoinedMembers, GetJoinedMembers.Response>(json, contentMappings) {
            handler.getJoinedMembers(this)
        }
        matrixEndpoint<GetEvents, GetEvents.Response>(json, contentMappings) {
            handler.getEvents(this)
        }
        matrixEndpoint<SendStateEvent, StateEventContent, SendEventResponse>(json, contentMappings) {
            handler.sendStateEvent(this)
        }
        matrixEndpoint<SendMessageEvent, MessageEventContent, SendEventResponse>(json, contentMappings) {
            handler.sendMessageEvent(this)
        }
        matrixEndpoint<RedactEvent, RedactEvent.Request, SendEventResponse>(json, contentMappings) {
            handler.redactEvent(this)
        }
        matrixEndpoint<CreateRoom, CreateRoom.Request, CreateRoom.Response>(json, contentMappings) {
            handler.createRoom(this)
        }
        matrixEndpoint<SetRoomAlias, SetRoomAlias.Request>(json, contentMappings) {
            handler.setRoomAlias(this)
        }
        matrixEndpoint<GetRoomAlias, GetRoomAlias.Response>(json, contentMappings) {
            handler.getRoomAlias(this)
        }
        matrixEndpoint<GetRoomAliases, GetRoomAliases.Response>(json, contentMappings) {
            handler.getRoomAliases(this)
        }
        matrixEndpoint<DeleteRoomAlias>(json, contentMappings) {
            handler.deleteRoomAlias(this)
        }
        matrixEndpoint<GetJoinedRooms, GetJoinedRooms.Response>(json, contentMappings) {
            handler.getJoinedRooms(this)
        }
        matrixEndpoint<InviteUser, InviteUser.Request>(json, contentMappings) {
            handler.inviteUser(this)
        }
        matrixEndpoint<KickUser, KickUser.Request>(json, contentMappings) {
            handler.kickUser(this)
        }
        matrixEndpoint<BanUser, BanUser.Request>(json, contentMappings) {
            handler.banUser(this)
        }
        matrixEndpoint<UnbanUser, UnbanUser.Request>(json, contentMappings) {
            handler.unbanUser(this)
        }
        matrixEndpoint<JoinRoom, JoinRoom.Request, JoinRoom.Response>(json, contentMappings) {
            handler.joinRoom(this)
        }
        matrixEndpoint<KnockRoom, KnockRoom.Request, KnockRoom.Response>(json, contentMappings) {
            handler.knockRoom(this)
        }
        matrixEndpoint<ForgetRoom>(json, contentMappings) {
            handler.forgetRoom(this)
        }
        matrixEndpoint<LeaveRoom, LeaveRoom.Request>(json, contentMappings) {
            handler.leaveRoom(this)
        }
        matrixEndpoint<SetReceipt>(json, contentMappings) {
            handler.setReceipt(this)
        }
        matrixEndpoint<SetReadMarkers, SetReadMarkers.Request>(json, contentMappings) {
            handler.setReadMarkers(this)
        }
        matrixEndpoint<SetTyping, SetTyping.Request>(json, contentMappings) {
            handler.setTyping(this)
        }
        matrixEndpoint<GetRoomAccountData, RoomAccountDataEventContent>(json, contentMappings) {
            handler.getAccountData(this)
        }
        matrixEndpoint<SetRoomAccountData, RoomAccountDataEventContent>(json, contentMappings) {
            handler.setAccountData(this)
        }
        matrixEndpoint<GetDirectoryVisibility, Unit, GetDirectoryVisibility.Response>(json, contentMappings) {
            handler.getDirectoryVisibility(this)
        }
        matrixEndpoint<SetDirectoryVisibility, SetDirectoryVisibility.Request, Unit>(json, contentMappings) {
            handler.setDirectoryVisibility(this)
        }
        matrixEndpoint<GetPublicRooms, Unit, GetPublicRoomsResponse>(json, contentMappings) {
            handler.getPublicRooms(this)
        }
        matrixEndpoint<GetPublicRoomsWithFilter, GetPublicRoomsWithFilter.Request, GetPublicRoomsResponse>(
            json,
            contentMappings
        ) {
            handler.getPublicRoomsWithFilter(this)
        }
        matrixEndpoint<GetRoomTags, TagEventContent>(json, contentMappings) {
            handler.getTags(this)
        }
        matrixEndpoint<SetRoomTag, TagEventContent.Tag>(json, contentMappings) {
            handler.setTag(this)
        }
        matrixEndpoint<DeleteRoomTag>(json, contentMappings) {
            handler.deleteTag(this)
        }
        matrixEndpoint<GetEventContext, GetEventContext.Response>(json, contentMappings) {
            handler.getEventContext(this)
        }
        matrixEndpoint<ReportEvent, ReportEvent.Request>(json, contentMappings) {
            handler.reportEvent(this)
        }
        matrixEndpoint<UpgradeRoom, UpgradeRoom.Request, UpgradeRoom.Response>(json, contentMappings) {
            handler.upgradeRoom(this)
        }
        matrixEndpoint<GetHierarchy, GetHierarchy.Response>(json, contentMappings) {
            handler.getHierarchy(this)
        }
    }
}