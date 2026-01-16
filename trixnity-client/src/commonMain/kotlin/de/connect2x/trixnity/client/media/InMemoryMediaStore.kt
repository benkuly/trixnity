package de.connect2x.trixnity.client.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.utils.ByteArrayFlow
import de.connect2x.trixnity.utils.toByteArray
import org.koin.dsl.module
import kotlin.time.Clock

internal class InMemoryMediaStore(
    coroutineScope: CoroutineScope,
    configuration: MatrixClientConfiguration,
    clock: Clock,
) : CachedMediaStore(coroutineScope, configuration, clock) {
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

    override suspend fun deleteAllFromStore() {
        media.value = mapOf()
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
            toByteArray(url, delegate, coroutineScope, expectedSize, maxSize)
                ?: if (maxSize != null) delegate.toByteArray(maxSize) else delegate.toByteArray()
    }
}

interface InMemoryPlatformMedia : PlatformMedia

fun MediaStoreModule.Companion.inMemory() = MediaStoreModule {
    module {
        single<MediaStore> {
            InMemoryMediaStore(
                coroutineScope = get(),
                configuration = get(),
                clock = get()
            )
        }
    }
}