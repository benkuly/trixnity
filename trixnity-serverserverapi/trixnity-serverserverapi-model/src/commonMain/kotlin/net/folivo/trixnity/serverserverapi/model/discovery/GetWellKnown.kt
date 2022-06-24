package net.folivo.trixnity.serverserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

/**
 * @see <a href="https://spec.matrix.org/v1.3/server-server-api/#getwell-knownmatrixserver">matrix spec</a>
 */
@Serializable
@Resource("/.well-known/matrix/server")
@HttpMethod(GET)
@WithoutAuth
object GetWellKnown : MatrixEndpoint<Unit, GetWellKnown.Response> {
    @Serializable
    data class Response(
        @SerialName("m.server") val server: String,
    )
}