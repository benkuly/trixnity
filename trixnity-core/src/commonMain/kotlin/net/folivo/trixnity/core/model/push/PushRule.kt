package net.folivo.trixnity.core.model.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#push-rules">matrix spec</a>
 */
@Serializable
data class PushRule(
    @SerialName("actions")
    val actions: Set<PushAction>,
    @SerialName("conditions")
    val conditions: Set<PushCondition>? = null,
    @SerialName("default")
    val default: Boolean,
    @SerialName("enabled")
    val enabled: Boolean,
    @SerialName("pattern")
    val pattern: String? = null,
    @SerialName("rule_id")
    val ruleId: String,
)