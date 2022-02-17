package net.folivo.trixnity.clientserverapi.model.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.push.PushRuleSet

@Serializable
data class GetPushRulesResponse(
    @SerialName("global") val global: PushRuleSet,
)