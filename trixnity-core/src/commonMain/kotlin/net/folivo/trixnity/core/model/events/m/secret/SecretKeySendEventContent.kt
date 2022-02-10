package net.folivo.trixnity.core.model.events.m.secret

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#msecretrequest">matrix spec</a>
 */
@Serializable
data class SecretKeySendEventContent(
    @SerialName("request_id")
    val requestId: String,
    @SerialName("secret")
    val secret: String,
) : ToDeviceEventContent
