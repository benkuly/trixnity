package net.folivo.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.model.UserId

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3keyschanges">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/keys/changes")
@HttpMethod(GET)
data class GetKeyChanges(
    @SerialName("from") val from: String,
    @SerialName("to") val to: String,
) : MatrixEndpoint<Unit, GetKeyChanges.Response> {
    @Serializable
    data class Response(
        @SerialName("changed")
        val changed: Set<UserId>,
        @SerialName("left")
        val left: Set<UserId>
    )
}