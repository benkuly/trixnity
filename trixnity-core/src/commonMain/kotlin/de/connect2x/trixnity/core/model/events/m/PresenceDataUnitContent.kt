package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.EphemeralDataUnitContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#presence">matrix spec</a>
 */
@Serializable
data class PresenceDataUnitContent(
    @SerialName("push")
    val push: List<PresenceUpdate>,
) : EphemeralDataUnitContent {
    @Serializable
    data class PresenceUpdate(
        @SerialName("presence")
        val presence: Presence,
        @SerialName("user_id")
        val userId: UserId,
        @SerialName("last_active_ago")
        val lastActiveAgo: Long,
        @SerialName("currently_active")
        val isCurrentlyActive: Boolean? = null,
        @SerialName("status_msg")
        val statusMessage: String? = null
    )
}