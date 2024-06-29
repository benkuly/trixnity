package net.folivo.trixnity.client.media.okio

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.KeyedMutex
import net.folivo.trixnity.utils.readByteArrayFlow
import net.folivo.trixnity.utils.write
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import kotlin.coroutines.CoroutineContext

class OkioMediaStore(
    private val basePath: Path,
    private val fileSystem: FileSystem = defaultFileSystem,
    private val coroutineContext: CoroutineContext = ioContext,
) : MediaStore {

    private val downloadsPath = basePath.resolve("downloads")

    private val basePathLock = KeyedMutex<String>()
    private val downloadsLock = KeyedMutex<String>()

    override suspend fun init() = withContext(coroutineContext) {
        if (fileSystem.exists(basePath).not()) {
            fileSystem.createDirectories(basePath)
        }
        if (fileSystem.exists(downloadsPath).not()) {
            fileSystem.createDirectories(downloadsPath)
        }
    }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() = withContext(coroutineContext) {
        fileSystem.deleteRecursively(basePath)
        init()
    }

    private fun Path.resolveUrl(url: String) =
        resolve(url.encodeToByteArray().toByteString().sha256().base64Url())

    override suspend fun addMedia(url: String, content: ByteArrayFlow) = downloadsLock.withLock(url) {
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

    override suspend fun getMedia(url: String): ByteArrayFlow? = basePathLock.withLock(url) {
        fileSystem.readByteArrayFlow(basePath.resolveUrl(url), coroutineContext)
    }

    override suspend fun deleteMedia(url: String) = withContext(coroutineContext) {
        basePathLock.withLock(url) {
            fileSystem.delete(basePath.resolveUrl(url))
        }
    }

    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) = withContext(coroutineContext) {
        basePathLock.withLock(oldUrl) {
            basePathLock.withLock(newUrl) {
                fileSystem.atomicMove(basePath.resolveUrl(oldUrl), basePath.resolveUrl(newUrl))
            }
        }
    }
}

internal expect val defaultFileSystem: FileSystem
internal expect val ioContext: CoroutineContext