package net.folivo.trixnity.client.api.model.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.push.PushAction
import net.folivo.trixnity.core.model.push.PushCondition

@Serializable
data class SetPushRuleRequest(
    @SerialName("actions")
    val actions: Set<PushAction>,
    @SerialName("conditions")
    val conditions: Set<PushCondition> = setOf(),
    @SerialName("pattern")
    val pattern: String? = null,
)