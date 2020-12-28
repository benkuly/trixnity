package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.StandardUnsignedData
import net.folivo.trixnity.core.model.events.StateEvent
import net.folivo.trixnity.core.model.events.m.room.JoinRulesEvent.JoinRulesEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-join-rules">matrix spec</a>
 */
@Serializable
data class JoinRulesEvent(
    @SerialName("content") override val content: JoinRulesEventContent,
    @SerialName("event_id") override val id: EventId,
    @SerialName("sender") override val sender: UserId,
    @SerialName("origin_server_ts") override val originTimestamp: Long,
    @SerialName("room_id") override val roomId: RoomId? = null,
    @SerialName("unsigned") override val unsigned: StandardUnsignedData,
    @SerialName("prev_content") override val previousContent: JoinRulesEventContent? = null,
    @SerialName("state_key") override val stateKey: String = "",
    @SerialName("type") override val type: String = "m.room.join_rules"
) : StateEvent<JoinRulesEventContent> {

    @Serializable
    data class JoinRulesEventContent(
        @SerialName("join_rule")
        val joinRule: JoinRule
    ) {
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
}