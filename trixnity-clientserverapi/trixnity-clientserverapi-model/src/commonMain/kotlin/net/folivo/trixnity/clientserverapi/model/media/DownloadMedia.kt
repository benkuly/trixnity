package net.folivo.trixnity.clientserverapi.model.media

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixmediav3downloadservernamemediaid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/media/v3/download/{serverName}/{mediaId}")
@HttpMethod(GET)
data class DownloadMedia(
    @SerialName("serverName") val serverName: String,
    @SerialName("mediaId") val mediaId: String,
    @SerialName("allow_remote") val allowRemote: Boolean? = null
) : MatrixEndpoint<Unit, Media> {

    @Transient
    override val requestContentType = ContentType.Application.Json

    @Transient
    override val responseContentType = ContentType.Application.OctetStream
}