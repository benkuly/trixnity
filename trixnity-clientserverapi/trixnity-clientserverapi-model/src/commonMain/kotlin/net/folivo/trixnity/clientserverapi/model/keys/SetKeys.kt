package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3keysupload">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/keys/upload")
@HttpMethod(POST)
data class SetKeys(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<SetKeys.Request, SetKeys.Response> {
    @Serializable
    data class Request(
        @SerialName("device_keys")
        val deviceKeys: SignedDeviceKeys?,
        @SerialName("one_time_keys")
        val oneTimeKeys: Keys?
    )

    @Serializable
    data class Response(
        @SerialName("one_time_key_counts")
        val oneTimeKeyCounts: Map<KeyAlgorithm, Int>,
    )
}