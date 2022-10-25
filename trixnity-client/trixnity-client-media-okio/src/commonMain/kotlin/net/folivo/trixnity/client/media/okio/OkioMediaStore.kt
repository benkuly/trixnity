package net.folivo.trixnity.client.media.okio

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.core.ByteFlow
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import kotlin.coroutines.CoroutineContext

class OkioMediaStore(
    private val basePath: Path,
    private val fileSystem: FileSystem = defaultFileSystem,
    private val context: CoroutineContext = defaultContext,
) : MediaStore {
    override suspend fun init() = withContext(context) {
        if (fileSystem.exists(basePath).not()) {
            fileSystem.createDirectories(basePath)
        }
    }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() = withContext(context) {
        fileSystem.deleteRecursively(basePath)
        init()
    }

    private fun Path.resolveUrl(url: String) = resolve(url.encodeToByteArray().toByteString().base64())

    override suspend fun addMedia(url: String, content: ByteFlow) = withContext(context) {
        fileSystem.sink(basePath.resolveUrl(url)).buffer().use { sink ->
            content.collect { sink.writeByte(it.toInt()) }
        }
    }

    override suspend fun getMedia(url: String): ByteFlow? = withContext(context) {
        val path = basePath.resolveUrl(url)
        if (fileSystem.exists(path))
            flow {
                fileSystem.source(path).buffer().use { source ->
                    while (source.exhausted().not()) {
                        emit(source.readByte())
                    }
                }
            }
        else null
    }

    override suspend fun deleteMedia(url: String) = withContext(context) {
        fileSystem.delete(basePath.resolveUrl(url))
    }

    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) = withContext(context) {
        fileSystem.atomicMove(basePath.resolveUrl(oldUrl), basePath.resolveUrl(newUrl))
    }
}

internal expect val defaultFileSystem: FileSystem
internal expect val defaultContext: CoroutineContext