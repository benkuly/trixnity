package net.folivo.trixnity.core.model.events.m.key.verification

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

/**
 * @see <a href="https://matrix.org/docs/spec/client_server/r0.6.1#m-key-verification-start">matrix spec</a>
 */
@Serializable // TODO this should be sealed and contain m.sas.v1 and custom serializer based on method key
data class StartEventContent(
    @SerialName("from_device")
    val fromDevice: String,
    @SerialName("transaction_id")
    val transactionId: String,
    @SerialName("method")
    val method: String,
    @SerialName("next_method")
    val nextMethod: String? = null
) : ToDeviceEventContent