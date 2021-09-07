package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.EphemeralEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-presence">matrix spec</a>
 */
@Serializable
data class PresenceEventContent(
    @SerialName("presence")
    val presence: Presence,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("displayname")
    val displayName: String? = null,
    @SerialName("last_active_ago")
    val lastActiveAgo: Long? = null,
    @SerialName("currently_active")
    val isCurrentlyActive: Boolean? = null,
    @SerialName("status_msg")
    val statusMessage: String? = null
) : EphemeralEventContent {
    @Serializable
    enum class Presence(val value: String) {
        @SerialName("online")
        ONLINE("online"),

        @SerialName("offline")
        OFFLINE("offline"),

        @SerialName("unavailable")
        UNAVAILABLE("unavailable")
    }
}