package de.connect2x.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.m.Mentions
import de.connect2x.trixnity.core.model.events.m.RelatesTo

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationaccept">matrix spec</a>
 */
@Serializable
data class SasAcceptEventContent(
    @SerialName("commitment")
    val commitment: String,
    @SerialName("hash")
    val hash: SasHash,
    @SerialName("key_agreement_protocol")
    val keyAgreementProtocol: SasKeyAgreementProtocol,
    @SerialName("message_authentication_code")
    val messageAuthenticationCode: SasMessageAuthenticationCode,
    @SerialName("short_authentication_string")
    val shortAuthenticationString: Set<SasMethod>,
    @SerialName("m.relates_to")
    override val relatesTo: RelatesTo.Reference?,
    @SerialName("transaction_id")
    override val transactionId: String?,
) : VerificationStep {
    override val mentions: Mentions? = null
    override val externalUrl: String? = null

    override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo as? RelatesTo.Reference)
}