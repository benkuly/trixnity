package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys

@Serializable
@Resource("/_matrix/client/v3/keys/device_signing/upload")
data class SetCrossSigningKeys(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<SetCrossSigningKeys.Request, Unit>() {
    @Transient
    override val method = Post

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