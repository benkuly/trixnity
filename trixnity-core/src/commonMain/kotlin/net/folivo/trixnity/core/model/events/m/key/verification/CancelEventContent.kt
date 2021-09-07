package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-key-verification-cancel">matrix spec</a>
 */
@Serializable // TODO this should be sealed and contain m.sas.v1 and custom serializer based on method key
data class CancelEventContent(
    @SerialName("transaction_id")
    val transactionId: String,
    @SerialName("reason")
    val reason: String,
    @SerialName("code")
    val code: Code
) : ToDeviceEventContent {
    @Serializable
    enum class Code {
        @SerialName("m.user")
        USER,

        @SerialName("m.timeout")
        TIMEOUT,

        @SerialName("m.unknown_transaction")
        UNKNOWN_TRANSACTION,

        @SerialName("m.unknown_method")
        UNKNOWN_METHOD,

        @SerialName("m.unexpected_message")
        UNEXPECTED_MESSAGE,

        @SerialName("m.key_mismatch")
        KEY_MISMATCH,

        @SerialName("m.user_mismatch")
        USER_MISMATCH,

        @SerialName("m.invalid_message")
        INVALID_MESSAGE,

        @SerialName("m.accepted")
        ACCEPTED,
    }
}