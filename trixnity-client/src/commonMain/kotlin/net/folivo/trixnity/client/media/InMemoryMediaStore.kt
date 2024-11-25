package net.folivo.trixnity.client.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.toByteArray
import net.folivo.trixnity.utils.toByteArrayFlow

class InMemoryMediaStore : MediaStore {
    val media = MutableStateFlow<Map<String, ByteArray>>(mapOf())
    override suspend fun addMedia(url: String, content: ByteArrayFlow) {
        media.update { it + (url to content.toByteArray()) }
    }

    private inner class InMemoryPlatformMedia(private val delegate: ByteArrayFlow) : PlatformMedia,
        ByteArrayFlow by delegate {
        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): PlatformMedia =
            InMemoryPlatformMedia(delegate.let(transformer))
    }

    override suspend fun getMedia(url: String): PlatformMedia? =
        media.value[url]?.toByteArrayFlow()?.let(::InMemoryPlatformMedia)

    override suspend fun deleteMedia(url: String) {
        media.update { it - url }
    }

    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) {
        media.update {
            val value = it[oldUrl]
            if (value != null)
                it + (newUrl to value) - oldUrl
            else it
        }
    }

    override suspend fun clearCache() {
        media.value = mapOf()
    }

    override suspend fun deleteAll() {
        clearCache()
    }
}