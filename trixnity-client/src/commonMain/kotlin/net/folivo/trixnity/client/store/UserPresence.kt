package net.folivo.trixnity.client.store

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.m.Presence

@Serializable
data class UserPresence(
    val presence: Presence,
    /**
     * The instant, when the last update of the [UserPresence] arrived from sync.
     */
    val lastUpdate: Instant,
    /**
     * The instant, when the server marked the user as active.
     */
    val lastActive: Instant? = null,
    val isCurrentlyActive: Boolean? = null,
    val statusMessage: String? = null
)