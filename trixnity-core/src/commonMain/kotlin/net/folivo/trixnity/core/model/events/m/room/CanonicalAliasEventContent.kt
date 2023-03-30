package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.6/client-server-api/#mroomcanonical_alias">matrix spec</a>
 */
@Serializable
data class CanonicalAliasEventContent(
    @SerialName("alias")
    val alias: RoomAliasId? = null,
    @SerialName("alt_aliases")
    val aliases: Set<RoomAliasId>? = null
) : StateEventContent
