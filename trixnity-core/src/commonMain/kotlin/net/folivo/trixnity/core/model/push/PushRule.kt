package net.folivo.trixnity.core.model.push

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#push-rules">matrix spec</a>
 */
sealed interface PushRule {
    val kind: PushRuleKind
    val ruleId: String
    val default: Boolean
    val enabled: Boolean
    val actions: Set<PushAction>

    @Serializable
    data class Override(
        @SerialName("rule_id")
        override val ruleId: String,
        @SerialName("default")
        override val default: Boolean = false,
        @SerialName("enabled")
        override val enabled: Boolean = false,
        @SerialName("actions")
        override val actions: Set<PushAction> = setOf(),
        @SerialName("conditions")
        val conditions: Set<PushCondition>? = null,
    ) : PushRule {
        @Transient
        override val kind: PushRuleKind = PushRuleKind.OVERRIDE
    }

    @Serializable
    data class Content(
        @SerialName("rule_id")
        override val ruleId: String,
        @SerialName("default")
        override val default: Boolean = false,
        @SerialName("enabled")
        override val enabled: Boolean = false,
        @SerialName("actions")
        override val actions: Set<PushAction> = setOf(),
        @SerialName("pattern")
        val pattern: String,
    ) : PushRule {
        @Transient
        override val kind: PushRuleKind = PushRuleKind.CONTENT
    }

    @Serializable
    data class Room(
        @SerialName("rule_id")
        val roomId: RoomId,
        @SerialName("default")
        override val default: Boolean = false,
        @SerialName("enabled")
        override val enabled: Boolean = false,
        @SerialName("actions")
        override val actions: Set<PushAction> = setOf(),
    ) : PushRule {
        @Transient
        override val ruleId: String = roomId.full

        @Transient
        override val kind: PushRuleKind = PushRuleKind.ROOM
    }

    @Serializable
    data class Sender(
        @SerialName("rule_id")
        val userId: UserId,
        @SerialName("default")
        override val default: Boolean = false,
        @SerialName("enabled")
        override val enabled: Boolean = false,
        @SerialName("actions")
        override val actions: Set<PushAction> = setOf(),
    ) : PushRule {
        @Transient
        override val ruleId: String = userId.full

        @Transient
        override val kind: PushRuleKind = PushRuleKind.SENDER
    }

    @Serializable
    data class Underride(
        @SerialName("rule_id")
        override val ruleId: String,
        @SerialName("default")
        override val default: Boolean = false,
        @SerialName("enabled")
        override val enabled: Boolean = false,
        @SerialName("actions")
        override val actions: Set<PushAction> = setOf(),
        @SerialName("conditions")
        val conditions: Set<PushCondition>? = null,
    ) : PushRule {
        @Transient
        override val kind: PushRuleKind = PushRuleKind.UNDERRIDE
    }
}