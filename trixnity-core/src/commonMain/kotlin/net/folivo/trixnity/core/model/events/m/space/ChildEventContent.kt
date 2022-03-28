package net.folivo.trixnity.core.model.events.m.space

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mspacechild">matrix spec</a>
 */
@Serializable
data class ChildEventContent(
    @SerialName("order")
    val order: String? = null,
    @SerialName("suggested")
    val suggested: Boolean = false,
    @SerialName("via")
    val via: Set<String>? = null,
) : StateEventContent