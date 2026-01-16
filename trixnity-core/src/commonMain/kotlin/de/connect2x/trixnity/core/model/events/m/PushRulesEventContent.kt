package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import de.connect2x.trixnity.core.model.push.PushRuleSet

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mpush_rules">matrix spec</a>
 */
@Serializable
data class PushRulesEventContent(
    @SerialName("global") val global: PushRuleSet? = null,
) : GlobalAccountDataEventContent