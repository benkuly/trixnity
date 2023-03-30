package net.folivo.trixnity.serverserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

/**
 * @see <a href="https://spec.matrix.org/v1.6/server-server-api/#get_matrixfederationv1version">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/federation/v1/version")
@HttpMethod(GET)
@WithoutAuth
object GetServerVersion : MatrixEndpoint<Unit, GetServerVersion.Response> {
    @Serializable
    data class Response(
        @SerialName("server") val server: Server,
    ) {
        @Serializable
        data class Server(
            @SerialName("name") val name: String,
            @SerialName("version") val version: String,
        )
    }
}