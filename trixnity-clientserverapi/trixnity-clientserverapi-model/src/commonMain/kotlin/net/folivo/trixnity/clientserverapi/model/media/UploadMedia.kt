package net.folivo.trixnity.clientserverapi.model.media

import io.ktor.http.*
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.content.*
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixEndpoint

@Serializable
@Resource("/_matrix/media/v3/upload")
data class UploadMedia(
    @SerialName("filename") val filename: String? = null,
    @Transient
    override val requestContentType: ContentType = ContentType.Application.OctetStream
) : MatrixEndpoint<OutgoingContent.ReadChannelContent, UploadMedia.Response> {
    @Transient
    override val method = Post

    @Transient
    override val responseContentType = ContentType.Application.Json

    @Serializable
    data class Response(
        @SerialName("content_uri") val contentUri: String,
    )
}