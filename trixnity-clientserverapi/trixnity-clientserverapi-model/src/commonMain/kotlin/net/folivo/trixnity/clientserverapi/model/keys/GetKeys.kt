package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

@Serializable
@Resource("/_matrix/client/v3/keys/query")
data class GetKeys(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<GetKeys.Request, GetKeys.Response>() {
    @Transient
    override val method = Post

    @Serializable
    data class Request(
        @SerialName("device_keys")
        val deviceKeys: Map<UserId, Set<String>>,
        @SerialName("token")
        val token: String?,
        @SerialName("timeout")
        val timeout: Int?,
    )

    @Serializable
    data class Response(
        @SerialName("failures")
        val failures: Map<UserId, JsonElement>?,
        @SerialName("device_keys")
        val deviceKeys: Map<UserId, Map<String, SignedDeviceKeys>>?,
        @SerialName("master_keys")
        val masterKeys: Map<UserId, SignedCrossSigningKeys>?,
        @SerialName("self_signing_keys")
        val selfSigningKeys: Map<UserId, SignedCrossSigningKeys>?,
        @SerialName("user_signing_keys")
        val userSigningKeys: Map<UserId, SignedCrossSigningKeys>?,
    )
}