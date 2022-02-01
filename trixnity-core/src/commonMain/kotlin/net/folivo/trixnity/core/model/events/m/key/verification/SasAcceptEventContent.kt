package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.SasMethod.DECIMAL
import net.folivo.trixnity.core.model.events.m.key.verification.SasMethod.EMOJI

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationaccept">matrix spec</a>
 */
@Serializable
data class SasAcceptEventContent(
    @SerialName("commitment")
    val commitment: String,
    @SerialName("hash")
    val hash: String = "sha256",
    @SerialName("key_agreement_protocol")
    val keyAgreementProtocol: String = "curve25519-hkdf-sha256",
    @SerialName("message_authentication_code")
    val messageAuthenticationCode: String = "hkdf-hmac-sha256",
    @SerialName("short_authentication_string")
    val shortAuthenticationString: Set<SasMethod> = setOf(DECIMAL, EMOJI),
    @SerialName("m.relates_to")
    override val relatesTo: RelatesTo.Reference?,
    @SerialName("transaction_id")
    override val transactionId: String?,
) : VerificationStep