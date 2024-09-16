package net.folivo.trixnity.clientserverapi.model.media

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.WithoutAuth

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixmediav3downloadservernamemediaid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/media/v3/download/{serverName}/{mediaId}")
@HttpMethod(GET)
@WithoutAuth
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