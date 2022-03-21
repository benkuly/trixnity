package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.model.uia.MatrixUIAEndpoint
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys

@Serializable
@Resource("/_matrix/client/v3/keys/device_signing/upload")
@HttpMethod(POST)
data class SetCrossSigningKeys(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixUIAEndpoint<SetCrossSigningKeys.Request, Unit> {
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