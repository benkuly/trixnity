package net.folivo.trixnity.client.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.toByteArray
import org.koin.dsl.module

@Deprecated("switch to createInMemoryMediaStoreModule", ReplaceWith("createInMemoryMediaStoreModule()"))
class InMemoryMediaStore(
    private val toByteArray: (suspend (uri: String, media: ByteArrayFlow, coroutineScope: CoroutineScope?, expectedSize: Long?, maxSize: Long?) -> ByteArray?)? = null,
) : MediaStore {
    val media = MutableStateFlow<Map<String, List<ByteArray>>>(mapOf())
    override suspend fun addMedia(url: String, content: ByteArrayFlow) {
        media.update { it + (url to content.toList()) }
    }

    override suspend fun getMedia(url: String): InMemoryPlatformMedia? =
        media.value[url]?.asFlow()?.let { InMemoryPlatformMediaImpl(url, it) }

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

    private inner class InMemoryPlatformMediaImpl(
        private val url: String,
        private val delegate: ByteArrayFlow,
    ) : InMemoryPlatformMedia,
        ByteArrayFlow by delegate {
        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): PlatformMedia =
            InMemoryPlatformMediaImpl(url, delegate.let(transformer))

        override suspend fun toByteArray(
            coroutineScope: CoroutineScope?,
            expectedSize: Long?,
            maxSize: Long?
        ): ByteArray? =
            toByteArray?.invoke(url, delegate, coroutineScope, expectedSize, maxSize)
                ?: if (maxSize != null) delegate.toByteArray(maxSize) else delegate.toByteArray()
    }
}

interface InMemoryPlatformMedia : PlatformMedia

internal class InMemoryCachedMediaStore(
    coroutineScope: CoroutineScope,
    configuration: MatrixClientConfiguration,
    clock: Clock,
) : CachedMediaStore(coroutineScope, configuration, clock) {
    @Suppress("DEPRECATION")
    private val delegate = InMemoryMediaStore(::toByteArray)

    override suspend fun init(coroutineScope: CoroutineScope) = delegate.init(coroutineScope)
    override suspend fun addMedia(url: String, content: ByteArrayFlow) = delegate.addMedia(url, content)
    override suspend fun getMedia(url: String): PlatformMedia? = delegate.getMedia(url)
    override suspend fun deleteMedia(url: String) = delegate.deleteMedia(url)
    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) = delegate.changeMediaUrl(oldUrl, newUrl)
    override suspend fun clearCache() = delegate.clearCache()
    override suspend fun deleteAll() = delegate.deleteAll()
}

fun createInMemoryMediaStoreModule() = module {
    single<MediaStore> {
        InMemoryCachedMediaStore(
            coroutineScope = get(),
            configuration = get(),
            clock = get()
        )
    }
}