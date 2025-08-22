package net.folivo.trixnity.client.media.okio

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.media.CachedMediaStore
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.media.PlatformMedia
import net.folivo.trixnity.utils.*
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.time.Clock

private val log = KotlinLogging.logger("net.folivo.trixnity.client.media.okio.OkioMediaStore")

@Deprecated("switch to createOkioMediaStoreModule", ReplaceWith("createOkioMediaStoreModule(basePath)"))
class OkioMediaStore(
    private val basePath: Path,
    private val fileSystem: FileSystem = defaultFileSystem,
    private val coroutineContext: CoroutineContext = ioContext,
    private val toByteArray: (suspend (uri: String, media: ByteArrayFlow, coroutineScope: CoroutineScope?, expectedSize: Long?, maxSize: Long?) -> ByteArray?)? = null,
) : MediaStore {
    private val tmpPath = basePath.resolve("tmp")
    private val downloadsPath = basePath.resolve("downloads")

    private val basePathLock = KeyedMutex<String>()
    private val downloadsLock = KeyedMutex<String>()

    private fun createDirs() {
        if (fileSystem.exists(basePath).not()) {
            fileSystem.createDirectories(basePath)
        }
        if (fileSystem.exists(downloadsPath).not()) {
            fileSystem.createDirectories(downloadsPath)
        }
        if (fileSystem.exists(tmpPath).not()) {
            fileSystem.createDirectories(tmpPath)
        }
    }

    override suspend fun init(coroutineScope: CoroutineScope): Unit = withContext(coroutineContext) {
        fileSystem.deleteRecursively(tmpPath)
        createDirs()
        coroutineScope.coroutineContext.job.invokeOnCompletion {
            fileSystem.deleteRecursively(tmpPath)
        }
    }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() = withContext(coroutineContext) {
        fileSystem.deleteRecursively(basePath)
        createDirs()
    }

    private fun Path.resolveUrl(url: String) =
        resolve(url.encodeToByteArray().toByteString().sha256().base64Url())

    override suspend fun addMedia(url: String, content: ByteArrayFlow) = downloadsLock.withLock(url) {
        withContext(coroutineContext) {
            // It may happen that a download is aborted.
            // Saving into downloadsPath first, prevents [getMedia] to find that broken file.
            try {
                fileSystem.write(downloadsPath.resolveUrl(url), content, coroutineContext)
            } catch (throwable: Throwable) {
                fileSystem.delete(downloadsPath.resolveUrl(url))
                throw throwable
            }
            basePathLock.withLock(url) {
                fileSystem.atomicMove(downloadsPath.resolveUrl(url), basePath.resolveUrl(url))
            }
        }
    }

    override suspend fun getMedia(url: String): OkioPlatformMedia? = basePathLock.withLock(url) {
        withContext(coroutineContext) {
            val file = basePath.resolveUrl(url)
            if (fileSystem.exists(file)) FileBasedOkioPlatformMediaImpl(url, file)
            else null
        }
    }

    override suspend fun deleteMedia(url: String) = basePathLock.withLock(url) {
        withContext(coroutineContext) {
            fileSystem.delete(basePath.resolveUrl(url))
        }
    }

    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) =
        basePathLock.withLock(oldUrl) {
            basePathLock.withLock(newUrl) {
                withContext(coroutineContext) {
                    try {
                        fileSystem.atomicMove(basePath.resolveUrl(oldUrl), basePath.resolveUrl(newUrl))
                    } catch (exception: Exception) {
                        log.error(exception) { "could not change media url" }
                    }
                }
            }
        }

    private inner class FileBasedOkioPlatformMediaImpl(
        private val url: String,
        private val file: Path,
    ) : OkioPlatformMedia {
        private val delegate = byteArrayFlowFromSource(coroutineContext) { fileSystem.source(file) }

        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): OkioPlatformMedia =
            OkioPlatformMediaImpl(url, file, delegate.let(transformer))

        override suspend fun getTemporaryFile(): Result<OkioPlatformMedia.TemporaryFile> =
            runCatching {
                basePathLock.withLock(url) {
                    withContext(coroutineContext) {
                        val tmpFile = tmpPath.resolve(Random.nextString(12))
                        try {
                            fileSystem.copy(file, tmpFile)
                        } catch (throwable: Throwable) {
                            fileSystem.delete(tmpFile)
                            throw throwable
                        }
                        OkioPlatformMediaTemporaryFileImpl(tmpFile)
                    }
                }
            }

        override suspend fun collect(collector: FlowCollector<ByteArray>) = delegate.collect(collector)

        override suspend fun toByteArray(
            coroutineScope: CoroutineScope?,
            expectedSize: Long?,
            maxSize: Long?
        ): ByteArray? =
            toByteArray?.invoke(url, delegate, coroutineScope, expectedSize, maxSize)
                ?: if (maxSize != null) delegate.toByteArray(maxSize) else delegate.toByteArray()
    }

    private inner class OkioPlatformMediaImpl(
        private val url: String,
        private val file: Path,
        private val delegate: ByteArrayFlow,
    ) : OkioPlatformMedia, ByteArrayFlow by delegate {
        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): OkioPlatformMedia =
            OkioPlatformMediaImpl(url, file, delegate.let(transformer))

        override suspend fun getTemporaryFile(): Result<OkioPlatformMedia.TemporaryFile> =
            runCatching {
                basePathLock.withLock(url) {
                    withContext(coroutineContext) {
                        val tmpFile = tmpPath.resolve(Random.nextString(12))
                        try {
                            fileSystem.write(tmpFile, delegate, coroutineContext)
                        } catch (throwable: Throwable) {
                            fileSystem.delete(tmpFile)
                            throw throwable
                        }
                        OkioPlatformMediaTemporaryFileImpl(tmpFile)
                    }
                }
            }

        override suspend fun toByteArray(
            coroutineScope: CoroutineScope?,
            expectedSize: Long?,
            maxSize: Long?
        ): ByteArray? =
            toByteArray?.invoke(url, delegate, coroutineScope, expectedSize, maxSize)
                ?: if (maxSize != null) delegate.toByteArray(maxSize) else delegate.toByteArray()
    }

    private inner class OkioPlatformMediaTemporaryFileImpl(
        override val path: Path
    ) : OkioPlatformMedia.TemporaryFile {
        override suspend fun delete() {
            withContext(coroutineContext) {
                fileSystem.delete(path)
            }
        }
    }
}

internal class OkioCachedMediaStore(
    basePath: Path,
    fileSystem: FileSystem = defaultFileSystem,
    coroutineContext: CoroutineContext = ioContext,
    coroutineScope: CoroutineScope,
    configuration: MatrixClientConfiguration,
    clock: Clock,
) : CachedMediaStore(coroutineScope, configuration, clock) {
    @Suppress("DEPRECATION")
    private val delegate = OkioMediaStore(basePath, fileSystem, coroutineContext, ::toByteArray)

    override suspend fun init(coroutineScope: CoroutineScope) = delegate.init(coroutineScope)
    override suspend fun addMedia(url: String, content: ByteArrayFlow) = delegate.addMedia(url, content)
    override suspend fun getMedia(url: String): PlatformMedia? = delegate.getMedia(url)
    override suspend fun deleteMedia(url: String) = delegate.deleteMedia(url)
    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) = delegate.changeMediaUrl(oldUrl, newUrl)
    override suspend fun clearCache() = delegate.clearCache()
    override suspend fun deleteAll() = delegate.deleteAll()
}

fun createOkioMediaStoreModule(
    basePath: Path,
    fileSystem: FileSystem = defaultFileSystem,
    coroutineContext: CoroutineContext = ioContext,
) = module {
    single<MediaStore> {
        OkioCachedMediaStore(
            basePath = basePath,
            fileSystem = fileSystem,
            coroutineContext = coroutineContext,
            coroutineScope = get(),
            configuration = get(),
            clock = get()
        )
    }
}

internal expect val defaultFileSystem: FileSystem
internal expect val ioContext: CoroutineContext