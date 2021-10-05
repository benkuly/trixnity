package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent

data class RoomUser(
    val roomId: MatrixId.RoomId,
    val userId: MatrixId.UserId,
    val name: String,
    val event: Event<MemberEventContent>
)