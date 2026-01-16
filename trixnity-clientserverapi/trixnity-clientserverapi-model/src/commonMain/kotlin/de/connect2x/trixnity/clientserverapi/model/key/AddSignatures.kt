package de.connect2x.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixclientv3keyssignaturesupload">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/keys/signatures/upload")
@HttpMethod(POST)
data object AddSignatures : MatrixEndpoint<Map<UserId, Map<String, JsonElement>>, AddSignatures.Response> {
    @Serializable
    data class Response(
        @SerialName("failures")
        val failures: Map<UserId, Map<String, JsonElement>>,
    )
}