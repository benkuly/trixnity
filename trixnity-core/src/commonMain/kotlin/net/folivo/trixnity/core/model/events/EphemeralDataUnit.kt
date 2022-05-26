package net.folivo.trixnity.core.model.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.2/server-server-api/#edus">matrix spec</a>
 */
@Serializable
data class EphemeralDataUnit<C : EphemeralDataUnitContent>(
    @SerialName("content") val content: C,
)
