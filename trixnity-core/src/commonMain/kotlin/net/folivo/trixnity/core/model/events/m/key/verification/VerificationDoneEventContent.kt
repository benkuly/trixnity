package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationdone">matrix spec</a>
 */
@Serializable
data class VerificationDoneEventContent(
    @SerialName("m.relates_to")
    override val relatesTo: VerificationStepRelatesTo?,
    @SerialName("transaction_id")
    override val transactionId: String?,
) : VerificationStep