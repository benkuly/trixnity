package net.folivo.trixnity.clientserverapi.model.media

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#get_matrixmediav3config">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/media/v3/config")
@HttpMethod(GET)
object GetMediaConfig : MatrixEndpoint<Unit, GetMediaConfig.Response> {
    @Serializable
    data class Response(
        @SerialName("m.upload.size") val maxUploadSize: Long
    )
}