package net.folivo.trixnity.clientserverapi.model.media

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint

@Serializable
@Resource("/_matrix/media/v3/config")
object GetMediaConfig : MatrixJsonEndpoint<Unit, GetMediaConfig.Response>() {
    @Transient
    override val method = Get

    @Serializable
    data class Response(
        @SerialName("m.upload.size") val maxUploadSize: Int
    )
}