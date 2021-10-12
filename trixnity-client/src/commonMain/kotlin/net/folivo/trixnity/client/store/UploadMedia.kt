package net.folivo.trixnity.client.store

import io.ktor.http.*

data class UploadMedia(
    val cacheUri: String,
    val mxcUri: String? = null,
    val contentTyp: ContentType? = ContentType.Application.OctetStream,
)
