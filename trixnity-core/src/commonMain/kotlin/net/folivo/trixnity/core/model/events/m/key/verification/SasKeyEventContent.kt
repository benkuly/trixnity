package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.keys.KeyValue

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationkey">matrix spec</a>
 */
@Serializable
data class SasKeyEventContent(
    @SerialName("key")
    val key: KeyValue.Curve25519KeyValue,
    @SerialName("m.relates_to")
    override val relatesTo: RelatesTo.Reference?,
    @SerialName("transaction_id")
    override val transactionId: String?,
) : VerificationStep {
    override val mentions: Mentions? = null
    override val externalUrl: String? = null

    override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo as? RelatesTo.Reference)
}