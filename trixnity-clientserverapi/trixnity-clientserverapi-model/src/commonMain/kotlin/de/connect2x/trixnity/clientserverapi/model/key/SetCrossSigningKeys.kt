package de.connect2x.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.model.keys.SignedCrossSigningKeys

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3keysdevice_signingupload">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/keys/device_signing/upload")
@HttpMethod(POST)
data object SetCrossSigningKeys : MatrixUIAEndpoint<SetCrossSigningKeys.Request, Unit> {
    @Serializable
    data class Request(
        @SerialName("master_key")
        val masterKey: SignedCrossSigningKeys?,
        @SerialName("self_signing_key")
        val selfSigningKey: SignedCrossSigningKeys?,
        @SerialName("user_signing_key")
        val userSigningKey: SignedCrossSigningKeys?,
    )
}