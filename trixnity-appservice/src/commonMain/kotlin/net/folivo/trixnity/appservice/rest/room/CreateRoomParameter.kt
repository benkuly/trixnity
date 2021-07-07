package net.folivo.trixnity.appservice.rest.room

import net.folivo.trixnity.client.rest.api.room.CreateRoomRequest
import net.folivo.trixnity.client.rest.api.room.Visibility
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event.StateEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent

data class CreateRoomParameter(
    val visibility: Visibility = Visibility.PUBLIC,
    val name: String? = null,
    val topic: String? = null,
    val invite: Set<MatrixId.UserId>? = null,
    val invite3Pid: Set<CreateRoomRequest.Invite3Pid>? = null,
    val roomVersion: String? = null,
    val creationContent: CreateEventContent? = null,
    val initialState: List<StateEvent<*>>? = null,
    val preset: CreateRoomRequest.Preset? = null,
    val isDirect: Boolean? = null,
    val powerLevelContentOverride: PowerLevelsEventContent? = null,
    val asUserId: MatrixId.UserId? = null
)