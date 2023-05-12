package net.folivo.trixnity.client.media.okio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.core.ByteArrayFlow
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import kotlin.coroutines.CoroutineContext

class OkioMediaStore(
    private val basePath: Path,
    private val fileSystem: FileSystem = defaultFileSystem,
    private val coroutineContext: CoroutineContext = defaultContext,
) : MediaStore {

    private val writeLock = MutableStateFlow(setOf<String>())

    private suspend fun <T> withLock(vararg keys: String, block: suspend () -> T): T = waitLock(*keys) {
        try {
            writeLock.update { it + keys }
            block()
        } finally {
            writeLock.update { it - keys.toSet() }
        }
    }

    private suspend fun <T> waitLock(vararg keys: String, block: suspend () -> T): T {
        writeLock.first { lock -> keys.none { lock.contains(it) } }
        return block()
    }

    override suspend fun init() = withContext(coroutineContext) {
        if (fileSystem.exists(basePath).not()) {
            fileSystem.createDirectories(basePath)
        }
    }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() = withContext(coroutineContext) {
        fileSystem.deleteRecursively(basePath)
        init()
    }

    private fun Path.resolveUrl(url: String) =
        resolve(url.encodeToByteArray().toByteString().sha256().hex())

    override suspend fun addMedia(url: String, content: ByteArrayFlow) = withLock(url) {
        fileSystem.writeByteFlow(basePath.resolveUrl(url), content, coroutineContext)
    }

    override suspend fun getMedia(url: String): ByteArrayFlow? = waitLock(url) {
        fileSystem.readByteFlow(basePath.resolveUrl(url), coroutineContext)
    }

    override suspend fun deleteMedia(url: String) = withContext(coroutineContext) {
        withLock(url) {
            fileSystem.delete(basePath.resolveUrl(url))
        }
    }

    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) = withContext(coroutineContext) {
        withLock(oldUrl, newUrl) {
            fileSystem.atomicMove(basePath.resolveUrl(oldUrl), basePath.resolveUrl(newUrl))
        }
    }
}

internal expect val defaultFileSystem: FileSystem
internal expect val defaultContext: CoroutineContext