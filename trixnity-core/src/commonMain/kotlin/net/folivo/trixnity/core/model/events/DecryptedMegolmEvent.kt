package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId

@Serializable
data class DecryptedMegolmEvent<C : RoomEventContent>(
    @SerialName("content") val content: C,
    @SerialName("room_id") val roomId: RoomId
)