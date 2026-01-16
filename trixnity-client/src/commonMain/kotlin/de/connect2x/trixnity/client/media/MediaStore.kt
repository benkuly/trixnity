package de.connect2x.trixnity.client.media

import de.connect2x.trixnity.client.store.Store
import de.connect2x.trixnity.utils.ByteArrayFlow

interface MediaStore : Store {
    suspend fun addMedia(url: String, content: ByteArrayFlow)

    suspend fun getMedia(url: String): PlatformMedia?

    suspend fun deleteMedia(url: String)

    suspend fun changeMediaUrl(oldUrl: String, newUrl: String)
}