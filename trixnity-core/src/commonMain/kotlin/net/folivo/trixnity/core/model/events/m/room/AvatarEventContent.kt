package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#mroomavatar">matrix spec</a>
 */
@Serializable
data class AvatarEventContent(
    @SerialName("url")
    val url: String? = null,
    @SerialName("info")
    val info: ImageInfo? = null
) : StateEventContent