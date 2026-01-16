package de.connect2x.trixnity.clientserverapi.model.server

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint

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