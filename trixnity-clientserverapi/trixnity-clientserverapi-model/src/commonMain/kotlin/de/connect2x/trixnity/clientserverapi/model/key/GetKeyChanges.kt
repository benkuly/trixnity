package de.connect2x.trixnity.clientserverapi.model.key

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.UserId

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