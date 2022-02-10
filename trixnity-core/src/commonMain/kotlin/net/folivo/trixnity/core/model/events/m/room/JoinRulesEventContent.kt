package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#mroomjoin_rules">matrix spec</a>
 */
@Serializable
data class JoinRulesEventContent(
    @SerialName("join_rule")
    val joinRule: JoinRule
) : StateEventContent {
    @Serializable
    enum class JoinRule { // TODO add unknown
        @SerialName("public")
        PUBLIC,

        @SerialName("knock")
        KNOCK,

        @SerialName("invite")
        INVITE,

        @SerialName("private")
        PRIVATE
    }
}