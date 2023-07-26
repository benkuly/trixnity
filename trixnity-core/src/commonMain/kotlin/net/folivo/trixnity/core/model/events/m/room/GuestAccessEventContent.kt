package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mroomguest_access">matrix spec</a>
 */
@Serializable
data class GuestAccessEventContent(
    @SerialName("guest_access")
    val guestAccess: GuestAccessType
) : StateEventContent {
    @Serializable
    enum class GuestAccessType {
        @SerialName("can_join")
        CAN_JOIN,

        @SerialName("forbidden")
        FORBIDDEN
    }
}