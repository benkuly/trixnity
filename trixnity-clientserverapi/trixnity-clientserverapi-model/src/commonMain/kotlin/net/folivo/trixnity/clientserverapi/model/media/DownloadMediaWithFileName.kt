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
 * @see <a href="https://spec.matrix.org/v1.11/client-server-api/#get_matrixclientv1mediadownloadservernamemediaidfilename">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/client/v1/media/download/{serverName}/{mediaId}/{fileName}")
@HttpMethod(GET)
data class DownloadMediaWithFileName(
    @SerialName("serverName") val serverName: String,
    @SerialName("mediaId") val mediaId: String,
    @SerialName("fileName") val fileName: String,
    @SerialName("timeout_ms") val timeoutMs: Long? = null,
) : MatrixEndpoint<Unit, Media> {

    @Transient
    override val requestContentType = null

    @Transient
    override val responseContentType = ContentType.Application.OctetStream
}