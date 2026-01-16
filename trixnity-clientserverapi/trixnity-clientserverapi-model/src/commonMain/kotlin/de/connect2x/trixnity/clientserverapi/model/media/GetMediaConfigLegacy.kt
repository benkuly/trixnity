package de.connect2x.trixnity.clientserverapi.model.media

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.connect2x.trixnity.core.Auth
import de.connect2x.trixnity.core.AuthRequired
import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.GET
import de.connect2x.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixmediav3config">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/media/v3/config")
@HttpMethod(GET)
@Deprecated("use GetMediaConfig instead")
object GetMediaConfigLegacy : MatrixEndpoint<Unit, GetMediaConfigLegacy.Response> {
    @Serializable
    data class Response(
        @SerialName("m.upload.size") val maxUploadSize: Long
    )
}