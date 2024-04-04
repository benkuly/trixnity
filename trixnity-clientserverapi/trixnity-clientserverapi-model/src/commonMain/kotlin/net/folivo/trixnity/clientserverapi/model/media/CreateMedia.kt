package net.folivo.trixnity.clientserverapi.model.media

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#post_matrixmediav1create">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/media/v1/create")
@HttpMethod(POST)
object CreateMedia : MatrixEndpoint<Unit, CreateMedia.Response> {
    @Serializable
    data class Response(
        @SerialName("content_uri") val contentUri: String,
        @SerialName("unused_expires_at") val unusedExpiresAt: Long? = null,
    )
}