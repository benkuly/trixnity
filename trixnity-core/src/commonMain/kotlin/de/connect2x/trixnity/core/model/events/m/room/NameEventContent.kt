package de.connect2x.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroomname">matrix spec</a>
 */
@Serializable
data class NameEventContent(
    @SerialName("name")
    val name: String,
    @SerialName("external_url")
    override val externalUrl: String? = null,
) : StateEventContent