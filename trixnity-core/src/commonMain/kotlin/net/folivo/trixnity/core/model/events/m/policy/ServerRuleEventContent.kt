package net.folivo.trixnity.core.model.events.m.policy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#mpolicyruleserver">matrix spec</a>
 */
@Serializable
data class ServerRuleEventContent(
    @SerialName("entity")
    val entity: String,
    @SerialName("reason")
    val reason: String,
    @SerialName("recommendation")
    val recommendation: String,
) : StateEventContent