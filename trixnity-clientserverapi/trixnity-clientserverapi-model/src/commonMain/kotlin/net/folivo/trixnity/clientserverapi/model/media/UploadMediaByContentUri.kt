package net.folivo.trixnity.clientserverapi.model.media

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#put_matrixmediav3uploadservernamemediaid">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/media/v3/upload/{serverName}/{mediaId}")
@HttpMethod(POST)
data class UploadMediaByContentUri(
    @SerialName("serverName") val serverName: String,
    @SerialName("mediaId") val mediaId: String,
    @SerialName("filename") val filename: String? = null
) : MatrixEndpoint<Media, Unit> {

    @Transient
    override val requestContentType: ContentType? = null

    @Transient
    override val responseContentType = ContentType.Application.Json
}