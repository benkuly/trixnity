package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-avatar">matrix spec</a>
 */
@Serializable
data class AvatarEventContent(
    @SerialName("url")
    val url: String,
    @SerialName("info")
    val info: ImageInfo? = null
) : StateEventContent