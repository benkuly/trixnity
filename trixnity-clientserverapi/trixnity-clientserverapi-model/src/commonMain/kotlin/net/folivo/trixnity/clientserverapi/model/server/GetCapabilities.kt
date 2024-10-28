package net.folivo.trixnity.clientserverapi.model.server

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientv3capabilities">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v3/capabilities")
@HttpMethod(GET)
object GetCapabilities : MatrixEndpoint<Unit, GetCapabilities.Response> {
    @Serializable
    data class Response(
        @SerialName("capabilities") val capabilities: Capabilities
    )
}