package net.folivo.trixnity.client.media.opfs

import js.objects.jso
import js.typedarrays.Uint8Array
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.KeyedMutex
import net.folivo.trixnity.utils.byteArrayFlowFromReadableStream
import net.folivo.trixnity.utils.writeTo
import okio.ByteString.Companion.toByteString
import web.fs.FileSystemDirectoryHandle
import web.streams.WritableStream

class OpfsMediaStore(private val basePath: FileSystemDirectoryHandle) : MediaStore {

    private val basePathLock = KeyedMutex<String>()

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        for (entry in basePath.values()) {
            basePath.removeEntry(entry.name)
        }
    }

    private fun fileSystemSafe(url: String) = url.encodeToByteArray().toByteString().sha256().base64Url()
    private suspend fun FileSystemDirectoryHandle.resolveUrl(url: String, create: Boolean = false) =
        getFileHandle(fileSystemSafe(url), jso { this.create = create })

    override suspend fun addMedia(url: String, content: ByteArrayFlow) = basePathLock.withLock(url) {
        @Suppress("UNCHECKED_CAST")
        val writableFileStream = basePath.resolveUrl(url, true).createWritable() as WritableStream<Uint8Array>
        try {
            content.writeTo(writableFileStream)
        } catch (throwable: Throwable) {
            basePath.removeEntry(fileSystemSafe(url))
            throw throwable
        }
    }

    override suspend fun getMedia(url: String): ByteArrayFlow? = basePathLock.withLock(url) {
        val fileHandle = try {
            basePath.resolveUrl(url).getFile()
        } catch (throwable: Throwable) {
            return@withLock null
        }
        byteArrayFlowFromReadableStream { fileHandle.stream() }
    }

    override suspend fun deleteMedia(url: String) = basePathLock.withLock(url) {
        basePath.removeEntry(fileSystemSafe(url))
    }

    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) = basePathLock.withLock(oldUrl) {
        val source = basePath.resolveUrl(oldUrl).getFile()
        basePathLock.withLock(newUrl) {
            val writableFileStream = basePath.resolveUrl(newUrl, true).createWritable()
            try {
                writableFileStream.write(source)
            } catch (throwable: Throwable) {
                basePath.removeEntry(fileSystemSafe(newUrl))
                throw throwable
            }
        }
        basePath.removeEntry(fileSystemSafe(oldUrl))
    }
}