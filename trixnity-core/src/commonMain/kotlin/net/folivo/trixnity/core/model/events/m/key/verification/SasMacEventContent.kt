package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.keys.Keys

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationmac">matrix spec</a>
 */
@Serializable
data class SasMacEventContent(
    @SerialName("keys")
    val keys: String,
    @SerialName("mac")
    val mac: Keys, // actually these are no keys, but they are serialized the same, so we recycle the Keys stuff.
    @SerialName("m.relates_to")
    override val relatesTo: VerificationStepRelatesTo?,
    @SerialName("transaction_id")
    override val transactionId: String?,
) : VerificationStep