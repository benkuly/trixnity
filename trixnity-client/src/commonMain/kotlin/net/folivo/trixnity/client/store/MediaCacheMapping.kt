package net.folivo.trixnity.client.store

import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class MediaCacheMapping(
    val cacheUri: String,
    val mxcUri: String? = null,
    val size: Int? = null,
    val contentType: String? = ContentType.Application.OctetStream.toString(),
)
