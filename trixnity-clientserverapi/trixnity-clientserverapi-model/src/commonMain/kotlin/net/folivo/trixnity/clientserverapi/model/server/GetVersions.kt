package net.folivo.trixnity.clientserverapi.model.server

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.WithoutAuth

@Serializable
@Resource("/_matrix/client/versions")
@HttpMethod(GET)
@WithoutAuth
object GetVersions : MatrixEndpoint<Unit, GetVersions.Response> {
    @Serializable
    data class Response(
        @SerialName("versions") val versions: List<String>,
        @SerialName("unstable_features") val unstable_features: Map<String, Boolean>
    )
}