package net.folivo.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.Keys

/**
 * @see <a href="https://spec.matrix.org/v1.7/server-server-api/#post_matrixfederationv1userkeysclaim">matrix spec</a>
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