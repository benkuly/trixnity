package net.folivo.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3keysupload">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/keys/upload")
@HttpMethod(POST)
data object SetKeys : MatrixEndpoint<SetKeys.Request, SetKeys.Response> {
    @Serializable
    data class Request(
        @SerialName("device_keys")
        val deviceKeys: SignedDeviceKeys? = null,
        @SerialName("one_time_keys")
        val oneTimeKeys: Keys? = null,
        @SerialName("fallback_keys")
        val fallbackKeys: Keys? = null,
    )

    @Serializable
    data class Response(
        @SerialName("one_time_key_counts")
        val oneTimeKeyCounts: Map<KeyAlgorithm, Int>,
    )
}