package net.folivo.trixnity.clientserverapi.model.media

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.MatrixEndpoint

@Serializable
@Resource("/_matrix/media/v3/config")
@HttpMethod(GET)
object GetMediaConfig : MatrixEndpoint<Unit, GetMediaConfig.Response> {
    @Serializable
    data class Response(
        @SerialName("m.upload.size") val maxUploadSize: Int
    )
}