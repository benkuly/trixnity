package net.folivo.trixnity.client.media.okio

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

    override suspend fun addMedia(url: String, content: ByteArrayFlow) =
        fileSystem.writeByteFlow(basePath.resolveUrl(url), content, coroutineContext)

    override suspend fun getMedia(url: String): ByteArrayFlow? =
        fileSystem.readByteFlow(basePath.resolveUrl(url), coroutineContext)

    override suspend fun deleteMedia(url: String) = withContext(coroutineContext) {
        fileSystem.delete(basePath.resolveUrl(url))
    }

    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) = withContext(coroutineContext) {
        fileSystem.atomicMove(basePath.resolveUrl(oldUrl), basePath.resolveUrl(newUrl))
    }
}

internal expect val defaultFileSystem: FileSystem
internal expect val defaultContext: CoroutineContext