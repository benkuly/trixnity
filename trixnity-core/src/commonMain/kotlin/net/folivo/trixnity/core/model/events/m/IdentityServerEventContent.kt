package net.folivo.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#midentity_server">matrix spec</a>
 */
@Serializable
data class IdentityServerEventContent(
    @SerialName("base_url")
    val baseUrl: String? = null,
) : GlobalAccountDataEventContent