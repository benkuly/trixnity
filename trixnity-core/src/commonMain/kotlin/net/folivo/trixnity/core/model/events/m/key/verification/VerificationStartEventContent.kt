package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.folivo.trixnity.core.model.events.RelatesTo

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationstart">matrix spec</a>
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("method")
sealed interface VerificationStartEventContent : VerificationStep {
    val fromDevice: String
    val nextMethod: VerificationMethod?

    /**
     * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationstartmsasv1">matrix spec</a>
     */
    @Serializable
    @SerialName("m.sas.v1")
    data class SasStartEventContent(
        @SerialName("from_device")
        override val fromDevice: String,
        @SerialName("hashes")
        val hashes: Set<SasHash>,
        @SerialName("key_agreement_protocols")
        val keyAgreementProtocols: Set<SasKeyAgreementProtocol>,
        @SerialName("message_authentication_codes")
        val messageAuthenticationCodes: Set<SasMessageAuthenticationCode>,
        @SerialName("short_authentication_string")
        val shortAuthenticationString: Set<SasMethod>,
        @SerialName("m.relates_to")
        override val relatesTo: RelatesTo.Reference?,
        @SerialName("transaction_id")
        override val transactionId: String?,
    ) : VerificationStartEventContent {
        @SerialName("next_method")
        override val nextMethod: VerificationMethod? = null
    }
}