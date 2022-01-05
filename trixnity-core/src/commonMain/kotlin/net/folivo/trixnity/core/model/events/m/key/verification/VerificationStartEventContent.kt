package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.folivo.trixnity.core.model.events.m.key.verification.SasMethod.DECIMAL
import net.folivo.trixnity.core.model.events.m.key.verification.SasMethod.EMOJI

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationstart">matrix spec</a>
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("method")
sealed class VerificationStartEventContent : VerificationStep {
    abstract val fromDevice: String
    abstract val nextMethod: VerificationMethod?

    /**
     * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationstartmsasv1">matrix spec</a>
     */
    @Serializable
    @SerialName("m.sas.v1")
    data class SasStartEventContent(
        @SerialName("from_device")
        override val fromDevice: String,
        @SerialName("hashes")
        val hashes: Set<String> = setOf("sha256"),
        @SerialName("key_agreement_protocols")
        val keyAgreementProtocols: Set<String> = setOf("curve25519-hkdf-sha256"),
        @SerialName("message_authentication_codes")
        val messageAuthenticationCodes: Set<String> = setOf("hkdf-hmac-sha256"),
        @SerialName("short_authentication_string")
        val shortAuthenticationString: Set<SasMethod> = setOf(DECIMAL, EMOJI),
        @SerialName("m.relates_to")
        override val relatesTo: VerificationStepRelatesTo?,
        @SerialName("transaction_id")
        override val transactionId: String?,
    ) : VerificationStartEventContent() {
        @SerialName("next_method")
        override val nextMethod: VerificationMethod? = null
    }
}