package net.folivo.trixnity.appservice.room

import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoomRequest
import net.folivo.trixnity.clientserverapi.model.rooms.Visibility
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent

data class CreateRoomParameter(
    val visibility: Visibility = Visibility.PUBLIC,
    val name: String? = null,
    val topic: String? = null,
    val invite: Set<UserId>? = null,
    val invite3Pid: Set<CreateRoomRequest.Invite3Pid>? = null,
    val roomVersion: String? = null,
    val creationContent: CreateEventContent? = null,
    val initialState: List<Event.InitialStateEvent<*>>? = null,
    val preset: CreateRoomRequest.Preset? = null,
    val isDirect: Boolean? = null,
    val powerLevelContentOverride: PowerLevelsEventContent? = null,
    val asUserId: UserId? = null
)