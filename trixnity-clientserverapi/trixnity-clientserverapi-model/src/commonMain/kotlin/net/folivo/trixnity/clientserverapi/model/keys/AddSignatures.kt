package net.folivo.trixnity.clientserverapi.model.keys

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.6/client-server-api/#post_matrixclientv3keyssignaturesupload">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/keys/signatures/upload")
@HttpMethod(POST)
data class AddSignatures(
    @SerialName("user_id") val asUserId: UserId? = null
) : MatrixEndpoint<Map<UserId, Map<String, JsonElement>>, AddSignatures.Response> {
    @Serializable
    data class Response(
        @SerialName("failures")
        val failures: Map<UserId, Map<String, JsonElement>>,
    )
}