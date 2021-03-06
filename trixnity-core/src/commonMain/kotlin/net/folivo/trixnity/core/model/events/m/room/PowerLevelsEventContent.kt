package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-power-levels">matrix spec</a>
 */
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
) : StateEventContent {
    @Serializable
    data class Notifications(
        @SerialName("room")
        val room: Int = 50
    )
}