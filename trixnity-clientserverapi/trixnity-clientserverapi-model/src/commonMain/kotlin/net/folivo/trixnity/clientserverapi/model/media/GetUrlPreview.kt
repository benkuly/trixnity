package net.folivo.trixnity.clientserverapi.model.media

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3preview_url">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/media/v3/preview_url")
@HttpMethod(GET)
data class GetUrlPreview(
    @SerialName("url") val url: String,
    @SerialName("ts") val timestamp: Long? = null,
) : MatrixEndpoint<Unit, GetUrlPreview.Response> {
    @Serializable
    data class Response(
        @SerialName("matrix:image:size") val size: Long? = null,
        @SerialName("og:image") val imageUrl: String? = null
    )
}