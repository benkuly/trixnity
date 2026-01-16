package de.connect2x.trixnity.core.model.events.m.secret

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.KeyRequestAction

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#msecretrequest">matrix spec</a>
 */
@Serializable
data class SecretKeyRequestEventContent(
    @SerialName("name")
    val name: String?,
    @SerialName("action")
    val action: KeyRequestAction,
    @SerialName("requesting_device_id")
    val requestingDeviceId: String,
    @SerialName("request_id")
    val requestId: String
) : ToDeviceEventContent
