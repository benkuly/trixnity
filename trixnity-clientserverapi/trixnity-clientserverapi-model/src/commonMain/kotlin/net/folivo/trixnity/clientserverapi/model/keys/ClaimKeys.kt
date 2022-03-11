package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.Keys

@Serializable
@Resource("/_matrix/client/v3/keys/claim")
data class ClaimKeys(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<ClaimKeys.Request, ClaimKeys.Response>() {
    @Transient
    override val method = Post

    @Serializable
    data class Request(
        @SerialName("one_time_keys")
        val oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
        @SerialName("timeout")
        val timeout: Int?,
    )

    @Serializable
    data class Response(
        @SerialName("failures")
        val failures: Map<String, JsonElement>,
        @SerialName("one_time_keys")
        val oneTimeKeys: Map<UserId, Map<String, Keys>>
    )
}