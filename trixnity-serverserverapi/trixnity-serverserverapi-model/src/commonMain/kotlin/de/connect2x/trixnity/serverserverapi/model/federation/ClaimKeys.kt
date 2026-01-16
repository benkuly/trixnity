package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
import de.connect2x.trixnity.core.model.keys.Keys

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#post_matrixfederationv1userkeysclaim">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/user/keys/claim")
@HttpMethod(POST)
object ClaimKeys : MatrixEndpoint<ClaimKeys.Request, ClaimKeys.Response> {
    @Serializable
    data class Request(
        @SerialName("one_time_keys")
        val oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
    )

    @Serializable
    data class Response(
        @SerialName("one_time_keys")
        val oneTimeKeys: Map<UserId, Map<String, Keys>>,
    )
}