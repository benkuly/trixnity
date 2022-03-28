package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.push.PushRules

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mpush_rules">matrix spec</a>
 */
@Serializable
data class PushRulesEventContent(
    @SerialName("global") val global: PushRules? = null,
) : GlobalAccountDataEventContent