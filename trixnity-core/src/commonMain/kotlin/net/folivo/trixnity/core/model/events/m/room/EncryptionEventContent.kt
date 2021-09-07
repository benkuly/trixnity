package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.crypto.EncryptionAlgorithm
import net.folivo.trixnity.core.model.events.StateEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-room-encryption">matrix spec</a>
 */
@Serializable
data class EncryptionEventContent(
    @SerialName("rotation_period_ms")
    val rotationPeriodMs: Int? = null,
    @SerialName("rotation_period_msgs")
    val rotationPeriodMsgs: Int? = null,
    @SerialName("algorithm")
    val algorithm: EncryptionAlgorithm = EncryptionAlgorithm.Megolm,
) : StateEventContent