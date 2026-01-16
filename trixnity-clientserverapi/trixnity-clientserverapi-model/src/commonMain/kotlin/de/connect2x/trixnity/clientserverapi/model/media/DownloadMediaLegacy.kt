package de.connect2x.trixnity.clientserverapi.model.media

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixmediav3downloadservernamemediaid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/media/v3/download/{serverName}/{mediaId}")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
@Deprecated("use DownloadMedia instead")
data class DownloadMediaLegacy(
    @SerialName("serverName") val serverName: String,
    @SerialName("mediaId") val mediaId: String,
    @SerialName("allow_remote") val allowRemote: Boolean? = null,
    @SerialName("allow_redirect") val allowRedirect: Boolean? = null,
    @SerialName("timeout_ms") val timeoutMs: Long? = null,
) : MatrixEndpoint<Unit, Media> {

    @Transient
    override val requestContentType = ContentType.Application.Json

    @Transient
    override val responseContentType = ContentType.Application.OctetStream
}