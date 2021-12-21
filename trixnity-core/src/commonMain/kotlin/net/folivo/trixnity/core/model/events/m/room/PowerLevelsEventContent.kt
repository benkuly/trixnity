package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.1/client-server-api/#mroompower_levels">matrix spec</a>
 */
@Serializable
data class PowerLevelsEventContent(
    @SerialName("ban")
    val ban: Int? = null,
    @SerialName("events")
    val events: Map<String, Int> = emptyMap(),
    @SerialName("events_default")
    val eventsDefault: Int? = null,
    @SerialName("invite")
    val invite: Int? = null,
    @SerialName("kick")
    val kick: Int? = null,
    @SerialName("redact")
    val redact: Int? = null,
    @SerialName("state_default")
    val stateDefault: Int? = null,
    @SerialName("users")
    val users: Map<UserId, Int> = emptyMap(),
    @SerialName("users_default")
    val usersDefault: Int? = null,
    @SerialName("notifications")
    val notifications: Notifications? = null
) : StateEventContent {
    @Serializable
    data class Notifications(
        @SerialName("room")
        val room: Int? = null
    )
}