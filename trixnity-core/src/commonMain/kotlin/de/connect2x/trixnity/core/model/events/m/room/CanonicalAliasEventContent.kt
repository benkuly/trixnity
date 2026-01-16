package de.connect2x.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroomcanonical_alias">matrix spec</a>
 */
@Serializable
data class CanonicalAliasEventContent(
    @SerialName("alias")
    val alias: RoomAliasId? = null,
    @SerialName("alt_aliases")
    val aliases: Set<RoomAliasId>? = null,
    @SerialName("external_url")
    override val externalUrl: String? = null,
) : StateEventContent
