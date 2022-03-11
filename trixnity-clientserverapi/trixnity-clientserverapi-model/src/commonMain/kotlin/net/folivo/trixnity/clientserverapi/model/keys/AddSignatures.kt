package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.MatrixJsonEndpoint
import net.folivo.trixnity.core.model.UserId

@Serializable
@Resource("/_matrix/client/v3/keys/signatures/upload")
data class AddSignatures(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixJsonEndpoint<Map<UserId, Map<String, JsonElement>>, AddSignatures.Response>() {
    @Transient
    override val method = Post

    @Serializable
    data class Response(
        @SerialName("failures")
        val failures: Map<UserId, Map<String, JsonElement>>,
    )
}