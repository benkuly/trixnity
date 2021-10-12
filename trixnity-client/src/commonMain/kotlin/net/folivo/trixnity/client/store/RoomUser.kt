package net.folivo.trixnity.client.store

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent

@Serializable
data class RoomUser(
    val roomId: MatrixId.RoomId,
    val userId: MatrixId.UserId,
    val name: String,
    val event: @Contextual Event<MemberEventContent>
)