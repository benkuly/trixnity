package de.connect2x.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
import de.connect2x.trixnity.core.model.keys.Keys

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3keysclaim">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/keys/claim")
@HttpMethod(POST)
data object ClaimKeys : MatrixEndpoint<ClaimKeys.Request, ClaimKeys.Response> {
    @Serializable
    data class Request(
        @SerialName("one_time_keys")
        val oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
        @SerialName("timeout")
        val timeout: Long?,
    )

    @Serializable
    data class Response(
        @SerialName("failures")
        val failures: Map<String, JsonElement>,
        @SerialName("one_time_keys")
        val oneTimeKeys: Map<UserId, Map<String, Keys>>
    )
}