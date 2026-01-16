package de.connect2x.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import de.connect2x.trixnity.core.model.events.m.Mentions
import de.connect2x.trixnity.core.model.events.m.RelatesTo

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationcancel">matrix spec</a>
 */
@Serializable
data class VerificationCancelEventContent(
    @SerialName("code")
    val code: Code,
    @SerialName("reason")
    val reason: String,
    @SerialName("m.relates_to")
    override val relatesTo: RelatesTo.Reference?,
    @SerialName("transaction_id")
    override val transactionId: String?,
) : VerificationStep {
    override val mentions: Mentions? = null
    override val externalUrl: String? = null

    @Serializable(with = Code.Serializer::class)
    sealed interface Code {
        val value: String

        /**
         * The user cancelled the verification.
         */
        data object User : Code {
            override val value = "m.user"
        }

        /**
         * The verification process timed out. Verification processes can define their own timeout parameters.
         */
        data object Timeout : Code {
            override val value = "m.timeout"
        }

        /**
         * The device does not know about the given transaction ID.
         */
        data object UnknownTransaction : Code {
            override val value = "m.unknown_transaction"
        }

        /**
         * The device does not know how to handle the requested method. This should be sent for m.key.verification.start
         * messages and messages defined by individual verification processes.
         *
         * For SAS: The devices are unable to agree on the key agreement, hash, MAC, or SAS method.
         */
        data object UnknownMethod : Code {
            override val value = "m.unknown_method"
        }

        /**
         * The device received an unexpected message. Typically raised, when one of the parties is handling the
         * verification out of order.
         */
        data object UnexpectedMessage : Code {
            override val value = "m.unexpected_message"
        }

        /**
         * The key was not verified.
         */
        data object KeyMismatch : Code {
            override val value = "m.key_mismatch"
        }

        /**
         * The expected user did not match the user verified.
         */
        data object UserMismatch : Code {
            override val value = "m.user_mismatch"
        }

        /**
         * The message received was invalid.
         */
        data object InvalidMessage : Code {
            override val value = "m.invalid_message"
        }

        /**
         * A m.key.verification.request was accepted by a different device. The device receiving this error can ignore
         * the verification request.
         */
        data object Accepted : Code {
            override val value = "m.accepted"
        }

        /**
         * For SAS: The hash commitment did not match.
         */
        data object MismatchedCommitment : Code {
            override val value = "m.mismatched_commitment"
        }

        /**
         * For SAS: The SAS did not match.
         */
        data object MismatchedSas : Code {
            override val value = "m.mismatched_sas"
        }

        /**
         * Internal error.
         */
        data object InternalError : Code {
            override val value = "de.connect2x.internal"
        }

        data class Unknown(override val value: String) : Code

        object Serializer : KSerializer<Code> {
            override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Code", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): Code {
                return when (val value = decoder.decodeString()) {
                    User.value -> User
                    Timeout.value -> Timeout
                    UnknownTransaction.value -> UnknownTransaction
                    UnknownMethod.value -> UnknownMethod
                    UnexpectedMessage.value -> UnexpectedMessage
                    KeyMismatch.value -> KeyMismatch
                    UserMismatch.value -> UserMismatch
                    InvalidMessage.value -> InvalidMessage
                    Accepted.value -> Accepted
                    MismatchedCommitment.value -> MismatchedCommitment
                    MismatchedSas.value -> MismatchedSas
                    else -> Unknown(value)
                }
            }

            override fun serialize(encoder: Encoder, value: Code) {
                encoder.encodeString(value.value)
            }
        }
    }

    override fun copyWith(relatesTo: RelatesTo?) = copy(relatesTo = relatesTo as? RelatesTo.Reference)
}