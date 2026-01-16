package de.connect2x.trixnity.clientserverapi.model.media

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixmediav3thumbnailservernamemediaid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/media/thumbnail/{serverName}/{mediaId}")
@HttpMethod(GET)
data class DownloadThumbnail(
    @SerialName("serverName") val serverName: String,
    @SerialName("mediaId") val mediaId: String,
    @SerialName("width") val width: Long,
    @SerialName("height") val height: Long,
    @SerialName("method") val method: ThumbnailResizingMethod? = null,
    @SerialName("animated") val animated: Boolean? = null,
    @SerialName("timeout_ms") val timeoutMs: Long? = null,
) : MatrixEndpoint<Unit, Media> {
    @Transient
    override val requestContentType = null

    @Transient
    override val responseContentType = ContentType.Application.OctetStream
}