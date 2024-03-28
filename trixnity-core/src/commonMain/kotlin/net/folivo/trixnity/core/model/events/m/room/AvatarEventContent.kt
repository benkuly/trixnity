package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroomavatar">matrix spec</a>
 */
@Serializable
data class AvatarEventContent(
    @SerialName("url")
    val url: String? = null,
    @SerialName("info")
    val info: ImageInfo? = null,
    @SerialName("external_url")
    override val externalUrl: String? = null,
) : StateEventContent