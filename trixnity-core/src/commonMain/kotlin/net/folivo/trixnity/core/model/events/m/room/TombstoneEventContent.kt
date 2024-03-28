package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroomtombstone">matrix spec</a>
 */
@Serializable
data class TombstoneEventContent(
    @SerialName("body")
    val body: String,
    @SerialName("replacement_room")
    val replacementRoom: RoomId,
    @SerialName("external_url")
    override val externalUrl: String? = null
) : StateEventContent