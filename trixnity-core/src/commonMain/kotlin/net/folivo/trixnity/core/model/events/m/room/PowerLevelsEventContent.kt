package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EventContent
import net.folivo.trixnity.core.model.events.EventType
import net.folivo.trixnity.core.model.events.StateEventContent
import kotlin.reflect.KClass

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mroompower_levels">matrix spec</a>
 */
@Serializable
data class PowerLevelsEventContent(
    @SerialName("ban")
    val ban: Int = BAN_DEFAULT,
    @SerialName("events")
    val events: Map<@Contextual EventType, Int> = emptyMap(),
    @SerialName("events_default")
    val eventsDefault: Int = EVENTS_DEFAULT,
    @SerialName("invite")
    val invite: Int = INVITE_DEFAULT,
    @SerialName("kick")
    val kick: Int = KICK_DEFAULT,
    @SerialName("redact")
    val redact: Int = REDACT_DEFAULT,
    @SerialName("state_default")
    val stateDefault: Int = STATE_DEFAULT,
    @SerialName("users")
    val users: Map<UserId, Int> = emptyMap(),
    @SerialName("users_default")
    val usersDefault: Int = USERS_DEFAULT,
    @SerialName("notifications")
    val notifications: Notifications? = null,
    @SerialName("external_url")
    override val externalUrl: String? = null,
) : StateEventContent {
    companion object {
        const val EVENTS_DEFAULT = 0
        const val STATE_DEFAULT = 50
        const val INVITE_DEFAULT = 0
        const val KICK_DEFAULT = 50
        const val BAN_DEFAULT = 50
        const val REDACT_DEFAULT = 50
        const val USERS_DEFAULT = 0
    }

    @Serializable
    data class Notifications(
        @SerialName("room")
        val room: Int = 50
    )
}

operator fun Map<EventType, Int>.get(kClass: KClass<out EventContent>): Int? =
    entries.find { it.key.kClass == kClass }?.value

operator fun Map<EventType, Int>.get(name: String): Int? =
    entries.find { it.key.name == name }?.value

inline fun <reified T : EventContent> Map<EventType, Int>.get(): Int? =
    get(T::class)