package net.folivo.trixnity.serverserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.HttpMethodType.GET

/**
 * @see <a href="https://spec.matrix.org/v1.10/server-server-api/#getwell-knownmatrixserver">matrix spec</a>
 */
@Serializable
@Resource("/.well-known/matrix/server")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
@ForceJson
object GetWellKnown : MatrixEndpoint<Unit, GetWellKnown.Response> {
    @Serializable
    data class Response(
        @SerialName("m.server") val server: String,
    )
}