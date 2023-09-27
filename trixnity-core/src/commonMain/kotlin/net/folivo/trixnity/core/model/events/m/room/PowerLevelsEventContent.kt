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
    val ban: Long = 50,
    @SerialName("events")
    val events: Map<@Contextual EventType, Long> = emptyMap(),
    @SerialName("events_default")
    val eventsDefault: Long = 0,
    @SerialName("invite")
    val invite: Long = 0,
    @SerialName("kick")
    val kick: Long = 50,
    @SerialName("redact")
    val redact: Long = 50,
    @SerialName("state_default")
    val stateDefault: Long = 50,
    @SerialName("users")
    val users: Map<UserId, Long> = emptyMap(),
    @SerialName("users_default")
    val usersDefault: Long = 0,
    @SerialName("notifications")
    val notifications: Notifications? = null
) : StateEventContent {
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