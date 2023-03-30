package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.6/client-server-api/#mroomtombstone">matrix spec</a>
 */
@Serializable
data class TombstoneEventContent(
    @SerialName("body")
    val body: String,
    @SerialName("replacement_room")
    val replacementRoom: RoomId,
) : StateEventContent