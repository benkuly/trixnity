package de.connect2x.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys

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