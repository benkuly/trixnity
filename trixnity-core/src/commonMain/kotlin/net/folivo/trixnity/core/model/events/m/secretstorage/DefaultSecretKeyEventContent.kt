package net.folivo.trixnity.core.model.events.m.secretstorage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.7/client-server-api/#key-storage">matrix spec</a>
 */
@Serializable
data class DefaultSecretKeyEventContent(
    @SerialName("key")
    val key: String
) : GlobalAccountDataEventContent
