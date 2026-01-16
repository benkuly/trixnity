package de.connect2x.trixnity.serverserverapi.model.federation

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.SignedCrossSigningKeys
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#post_matrixfederationv1userkeysquery">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/user/keys/query")
@HttpMethod(POST)
object GetKeys : MatrixEndpoint<GetKeys.Request, GetKeys.Response> {
    @Serializable
    data class Request(
        @SerialName("device_keys") val keysFrom: Map<UserId, Set<String>>
    )

    @Serializable
    data class Response(
        @SerialName("device_keys")
        val deviceKeys: Map<UserId, Map<String, SignedDeviceKeys>>,
        @SerialName("master_keys")
        val masterKeys: Map<UserId, SignedCrossSigningKeys>? = null,
        @SerialName("self_signing_keys")
        val selfSigningKeys: Map<UserId, SignedCrossSigningKeys>? = null,
    )
}

