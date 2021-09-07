package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.RoomAliasId
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-canonical-alias">matrix spec</a>
 */
@Serializable
data class CanonicalAliasEventContent(
    @SerialName("alias")
    val alias: RoomAliasId? = null,
    @SerialName("alt_aliases")
    val aliases: Set<RoomAliasId>? = null
) : StateEventContent
