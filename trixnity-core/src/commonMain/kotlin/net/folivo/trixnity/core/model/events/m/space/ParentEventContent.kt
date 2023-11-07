package net.folivo.trixnity.core.model.events.m.space

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mspaceparent">matrix spec</a>
 */
@Serializable
data class ParentEventContent(
    @SerialName("canonical")
    val canonical: Boolean = false,
    @SerialName("via")
    val via: Set<String>? = null,
    @SerialName("external_url")
    override val externalUrl: String? = null
) : StateEventContent