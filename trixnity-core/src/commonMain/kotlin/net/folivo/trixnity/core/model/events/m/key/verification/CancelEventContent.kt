package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.serialization.m.key.verification.CodeSerializer

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationcancel">matrix spec</a>
 */
@Serializable
data class CancelEventContent(
    @SerialName("code")
    val code: Code,
    @SerialName("reason")
    val reason: String,
    @SerialName("m.relates_to")
    override val relatesTo: VerificationStepRelatesTo?,
    @SerialName("transaction_id")
    override val transactionId: String?,
) : VerificationStep {
    @Serializable(with = CodeSerializer::class)
    sealed class Code {
        abstract val value: String

        /**
         * The user cancelled the verification.
         */
        object User : Code() {
            override val value = "m.user"
        }

        /**
         * The verification process timed out. Verification processes can define their own timeout parameters.
         */
        object Timeout : Code() {
            override val value = "m.timeout"
        }

        /**
         * The device does not know about the given transaction ID.
         */
        object UnknownTransaction : Code() {
            override val value = "m.unknown_transaction"
        }

        /**
         * The device does not know how to handle the requested method. This should be sent for m.key.verification.start
         * messages and messages defined by individual verification processes.
         *
         * For SAS: The devices are unable to agree on the key agreement, hash, MAC, or SAS method.
         */
        object UnknownMethod : Code() {
            override val value = "m.unknown_method"
        }

        /**
         * The device received an unexpected message. Typically raised, when one of the parties is handling the
         * verification out of order.
         */
        object UnexpectedMessage : Code() {
            override val value = "m.unexpected_message"
        }

        /**
         * The key was not verified.
         */
        object KeyMismatch : Code() {
            override val value = "m.key_mismatch"
        }

        /**
         * The expected user did not match the user verified.
         */
        object UserMismatch : Code() {
            override val value = "m.user_mismatch"
        }

        /**
         * The message received was invalid.
         */
        object InvalidMessage : Code() {
            override val value = "m.invalid_message"
        }

        /**
         * A m.key.verification.request was accepted by a different device. The device receiving this error can ignore
         * the verification request.
         */
        object Accepted : Code() {
            override val value = "m.accepted"
        }

        /**
         * For SAS: The hash commitment did not match.
         */
        object MismatchedCommitment : Code() {
            override val value = "m.mismatched_commitment"
        }

        /**
         * For SAS: The SAS did not match.
         */
        object MismatchedSas : Code() {
            override val value = "m.mismatched_sas"
        }

        /**
         * Internal error.
         */
        object InternalError : Code() {
            override val value = "net.folivo.internal"
        }

        data class Unknown(override val value: String) : Code()
    }
}