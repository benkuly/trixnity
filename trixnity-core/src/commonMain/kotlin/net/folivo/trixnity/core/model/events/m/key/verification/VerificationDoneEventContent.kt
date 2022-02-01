package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.RelatesTo

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationdone">matrix spec</a>
 */
@Serializable
data class VerificationDoneEventContent(
    @SerialName("m.relates_to")
    override val relatesTo: RelatesTo.Reference?,
    @SerialName("transaction_id")
    override val transactionId: String?,
) : VerificationStep