package net.folivo.trixnity.client.media

import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.ByteFlow

interface MediaStore : Store {
    suspend fun addMedia(url: String, content: ByteFlow)

    suspend fun getMedia(url: String): ByteFlow?

    suspend fun deleteMedia(url: String)

    suspend fun changeMediaUrl(oldUrl: String, newUrl: String)
}