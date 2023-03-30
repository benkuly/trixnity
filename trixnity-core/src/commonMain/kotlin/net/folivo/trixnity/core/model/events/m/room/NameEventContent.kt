package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.6/client-server-api/#mroomname">matrix spec</a>
 */
@Serializable
data class NameEventContent(
    @SerialName("name")
    val name: String
) : StateEventContent