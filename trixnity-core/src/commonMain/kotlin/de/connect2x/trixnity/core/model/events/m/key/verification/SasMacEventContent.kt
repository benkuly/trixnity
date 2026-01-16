package de.connect2x.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.keys.MacValue
import de.connect2x.trixnity.core.model.events.m.*
import de.connect2x.trixnity.core.model.keys.Keys

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationmac">matrix spec</a>
 */
@Serializable
data class SasMacEventContent(
    @SerialName("keys")
    val keys: MacValue,
    @SerialName("mac")
    val mac: Keys, // actually these are no keys, but they are serialized the same, so we recycle the Keys stuff.
    @SerialName("m.relates_to")
    override val relatesTo: RelatesTo.Reference?,
    @SerialName("transaction_id")
    override val transactionId: String?,
) : VerificationStep {
    override val mentions: Mentions? = null
    override val externalUrl: String? = null

    override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo as? RelatesTo.Reference)
}