package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-join-rules">matrix spec</a>
 */
@Serializable
data class JoinRulesEventContent(
    @SerialName("join_rule")
    val joinRule: JoinRule
) : StateEventContent {
    @Serializable
    enum class JoinRule {
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