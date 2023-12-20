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
    val ban: Long = BAN_DEFAULT,
    @SerialName("events")
    val events: Map<@Contextual EventType, Long> = emptyMap(),
    @SerialName("events_default")
    val eventsDefault: Long = EVENTS_DEFAULT,
    @SerialName("invite")
    val invite: Long = INVITE_DEFAULT,
    @SerialName("kick")
    val kick: Long = KICK_DEFAULT,
    @SerialName("redact")
    val redact: Long = REDACT_DEFAULT,
    @SerialName("state_default")
    val stateDefault: Long = STATE_DEFAULT,
    @SerialName("users")
    val users: Map<UserId, Long> = emptyMap(),
    @SerialName("users_default")
    val usersDefault: Long = USERS_DEFAULT,
    @SerialName("notifications")
    val notifications: Notifications? = null,
    @SerialName("external_url")
    override val externalUrl: String? = null,
) : StateEventContent {
    companion object {
        const val EVENTS_DEFAULT = 0L
        const val STATE_DEFAULT = 50L
        const val INVITE_DEFAULT = 0L
        const val KICK_DEFAULT = 50L
        const val BAN_DEFAULT = 50L
        const val REDACT_DEFAULT = 50L
        const val USERS_DEFAULT = 0L
    }

    @Serializable
    data class Notifications(
        @SerialName("room")
        val room: Long = 50
    )
}

operator fun Map<EventType, Long>.get(kClass: KClass<out EventContent>): Long? =
    entries.find { it.key.kClass == kClass }?.value

operator fun Map<EventType, Long>.get(name: String): Long? =
    entries.find { it.key.name == name }?.value

inline fun <reified T : EventContent> Map<EventType, Long>.get(): Long? =
    get(T::class)