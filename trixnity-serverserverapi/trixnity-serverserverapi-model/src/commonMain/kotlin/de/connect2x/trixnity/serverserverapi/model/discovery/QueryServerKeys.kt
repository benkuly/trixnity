package de.connect2x.trixnity.serverserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#post_matrixkeyv2query">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/key/v2/query")
@HttpMethod(POST)
@Auth(AuthRequired.NO)
object QueryServerKeys : MatrixEndpoint<QueryServerKeys.Request, QueryServerKeysResponse> {

    @Serializable
    data class Request(
        @SerialName("server_keys")
        val serverKeys: JsonObject,
    )
}