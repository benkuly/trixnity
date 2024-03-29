package net.folivo.trixnity.appservice

import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.clientserverapi.model.rooms.DirectoryVisibility
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent

data class CreateRoomParameter(
    val visibility: DirectoryVisibility = DirectoryVisibility.PUBLIC,
    val name: String? = null,
    val topic: String? = null,
    val invite: Set<UserId>? = null,
    val inviteThirdPid: Set<CreateRoom.Request.InviteThirdPid>? = null,
    val roomVersion: String? = null,
    val creationContent: CreateEventContent? = null,
    val initialState: List<InitialStateEvent<*>>? = null,
    val preset: CreateRoom.Request.Preset? = null,
    val isDirect: Boolean? = null,
    val powerLevelContentOverride: PowerLevelsEventContent? = null,
    val asUserId: UserId? = null
)