package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationready">matrix spec</a>
 */
@Serializable
data class VerificationReadyEventContent(
    @SerialName("from_device")
    val fromDevice: String,
    @SerialName("methods")
    val methods: Set<VerificationMethod>,
    @SerialName("m.relates_to")
    override val relatesTo: RelatesTo.Reference?,
    @SerialName("transaction_id")
    override val transactionId: String?,
) : VerificationStep {
    override val mentions: Mentions? = null
}