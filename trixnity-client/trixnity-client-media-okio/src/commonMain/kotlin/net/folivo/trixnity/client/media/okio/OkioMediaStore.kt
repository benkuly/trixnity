package net.folivo.trixnity.client.media.okio

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.utils.*
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

private val log = KotlinLogging.logger { }

class OkioMediaStore(
    private val basePath: Path,
    private val fileSystem: FileSystem = defaultFileSystem,
    private val coroutineContext: CoroutineContext = ioContext,
) : MediaStore {

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
            // It may happen, that a download is aborted.
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

    // #########################################
    // ############ temporary files ############
    // #########################################

    private val tmpPath = basePath.resolve("tmp")

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
    }

    private inner class OkioPlatformMediaTemporaryFileImpl(
        override val path: Path
    ) : OkioPlatformMedia.TemporaryFile {
        override suspend fun release() {
            withContext(coroutineContext) {
                fileSystem.delete(path)
            }
        }
    }
}

internal expect val defaultFileSystem: FileSystem
internal expect val ioContext: CoroutineContext