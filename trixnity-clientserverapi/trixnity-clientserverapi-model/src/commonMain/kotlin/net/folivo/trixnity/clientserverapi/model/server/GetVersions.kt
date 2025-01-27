package net.folivo.trixnity.clientserverapi.model.server

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.Auth
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixclientversions">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/versions")
@HttpMethod(GET)
@Auth(AuthRequired.OPTIONAL)
object GetVersions : MatrixEndpoint<Unit, GetVersions.Response> {
    @Serializable
    data class Response(
        @SerialName("versions") val versions: List<String> = listOf(),
        @SerialName("unstable_features") val unstableFeatures: Map<String, Boolean> = mapOf()
    )
}