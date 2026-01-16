package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#midentity_server">matrix spec</a>
 */
@Serializable
data class IdentityServerEventContent(
    @SerialName("base_url")
    val baseUrl: String? = null,
) : GlobalAccountDataEventContent