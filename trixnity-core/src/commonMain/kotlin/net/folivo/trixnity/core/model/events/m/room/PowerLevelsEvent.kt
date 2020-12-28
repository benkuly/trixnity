package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.*
import net.folivo.trixnity.core.model.events.StandardUnsignedData
import net.folivo.trixnity.core.model.events.StateEvent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-power-levels">matrix spec</a>
 */
@Serializable
data class PowerLevelsEvent(
    @SerialName("content") override val content: PowerLevelsEventContent,
    @SerialName("event_id") override val id: EventId,
    @SerialName("sender") override val sender: UserId,
    @SerialName("origin_server_ts") override val originTimestamp: Long,
    @SerialName("room_id") override val roomId: RoomId? = null,
    @SerialName("unsigned") override val unsigned: StandardUnsignedData,
    @SerialName("prev_content") override val previousContent: PowerLevelsEventContent? = null,
    @SerialName("state_key") override val stateKey: String = "",
    @SerialName("type") override val type: String = "m.room.power_levels"
) : StateEvent<PowerLevelsEvent.PowerLevelsEventContent> {

    @Serializable
    data class PowerLevelsEventContent(
        @SerialName("ban")
        val ban: Int = 50,
        @SerialName("events")
        val events: Map<String, Int> = emptyMap(),
        @SerialName("events_default")
        val eventsDefault: Int = 0,
        @SerialName("invite")
        val invite: Int = 50,
        @SerialName("kick")
        val kick: Int = 50,
        @SerialName("redact")
        val redact: Int = 50,
        @SerialName("state_default")
        val stateDefault: Int = 50,
        @SerialName("users")
        val users: Map<UserId, Int> = emptyMap(),
        @SerialName("users_default")
        val usersDefault: Int = 0,
        @SerialName("notifications")
        val notifications: Notifications? = null
    ) {
        @Serializable
        data class Notifications(
            @SerialName("room")
            val room: Int = 50
        )
    }
}