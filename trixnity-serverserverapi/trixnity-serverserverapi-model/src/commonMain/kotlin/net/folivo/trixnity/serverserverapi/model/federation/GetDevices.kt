package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

/**
 * @see <a href="https://spec.matrix.org/v1.6/server-server-api/#get_matrixfederationv1userdevicesuserid">matrix spec</a>
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

