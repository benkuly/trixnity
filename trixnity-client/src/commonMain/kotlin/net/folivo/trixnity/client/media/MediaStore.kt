package net.folivo.trixnity.client.media

import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.utils.ByteArrayFlow

interface MediaStore : Store {
    suspend fun addMedia(url: String, content: ByteArrayFlow)

    suspend fun getMedia(url: String): PlatformMedia?

    suspend fun deleteMedia(url: String)

    suspend fun changeMediaUrl(oldUrl: String, newUrl: String)
}