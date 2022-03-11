package net.folivo.trixnity.clientserverapi.model.server

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint

@Serializable
@Resource("/_matrix/client/versions")
object GetVersions : MatrixJsonEndpoint<Unit, GetVersions.Response>() {
    @Transient
    override val method = Get

    @Serializable
    data class Response(
        @SerialName("versions") val versions: List<String>,
        @SerialName("unstable_features") val unstable_features: Map<String, Boolean>
    )
}