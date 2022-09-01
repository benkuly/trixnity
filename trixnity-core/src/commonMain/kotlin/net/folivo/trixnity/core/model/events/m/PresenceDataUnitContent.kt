package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EphemeralEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.3/server-server-api/#presence">matrix spec</a>
 */
@Serializable
data class PresenceDataUnitContent(
    @SerialName("push")
    val push: List<PresenceUpdate>,
) : EphemeralEventContent {
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