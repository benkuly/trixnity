package net.folivo.trixnity.clientserverapi.model.media

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.POST
import net.folivo.trixnity.core.MatrixEndpoint

@Serializable
@Resource("/_matrix/media/v3/upload")
@HttpMethod(POST)
data class UploadMedia(
    @SerialName("filename") val filename: String? = null
) : MatrixEndpoint<Media, UploadMedia.Response> {

    @Transient
    override val requestContentType: ContentType? = null

    @Transient
    override val responseContentType = ContentType.Application.Json

    @Serializable
    data class Response(
        @SerialName("content_uri") val contentUri: String,
    )
}