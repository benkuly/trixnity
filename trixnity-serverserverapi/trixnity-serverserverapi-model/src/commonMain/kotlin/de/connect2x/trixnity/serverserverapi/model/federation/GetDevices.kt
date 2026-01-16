package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.SignedCrossSigningKeys
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#get_matrixfederationv1userdevicesuserid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/user/devices/{userId}")
@HttpMethod(GET)
data class GetDevices(
    @SerialName("userId") val userId: UserId
) : MatrixEndpoint<Unit, GetDevices.Response> {
    @Serializable
    data class Response(
        @SerialName("devices")
        val devices: Set<UserDevice>,
        @SerialName("master_key")
        val masterKey: SignedCrossSigningKeys? = null,
        @SerialName("self_signing_key")
        val selfSigningKey: SignedCrossSigningKeys? = null,
        @SerialName("stream_id")
        val streamId: Long,
        @SerialName("user_id")
        val userId: UserId
    ) {
        @Serializable
        data class UserDevice(
            @SerialName("device_display_name")
            val deviceDisplayName: String? = null,
            @SerialName("device_id")
            val deviceId: String,
            @SerialName("keys")
            val keys: SignedDeviceKeys,
        )
    }
}

