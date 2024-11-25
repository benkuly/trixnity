package net.folivo.trixnity.client.media.opfs

import io.github.oshai.kotlinlogging.KotlinLogging
import js.objects.jso
import js.typedarrays.Uint8Array
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.utils.*
import okio.ByteString.Companion.toByteString
import web.events.EventType
import web.events.addEventHandler
import web.file.File
import web.fs.FileSystemDirectoryHandle
import web.streams.WritableStream
import web.window.window
import kotlin.random.Random

private val log = KotlinLogging.logger {}

class OpfsMediaStore(private val basePath: FileSystemDirectoryHandle) : MediaStore {

    private val basePathLock = KeyedMutex<String>()

    override suspend fun init(coroutineScope: CoroutineScope) {
        suspend fun delTmp() {
            val tmpPath = tmpPath()
            basePath.removeEntry(tmpPath.name, jso { recursive = true })
            tmpPath()
        }

        delTmp()

        coroutineScope.coroutineContext.job.invokeOnCompletion {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.promise {
                delTmp()
            }
        }

        window.addEventHandler(EventType("unload")) {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.promise { delTmp() }
        }
    }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        for (entry in basePath.values()) {
            basePath.removeEntry(entry.name, jso { recursive = true })
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

    override suspend fun getMedia(url: String): OpfsPlatformMedia? = basePathLock.withLock(url) {
        val fileHandle = try {
            basePath.resolveUrl(url).getFile()
        } catch (_: Throwable) {
            return@withLock null
        }
        FileBasedOpfsPlatformMediaImpl(url, fileHandle)
    }

    override suspend fun deleteMedia(url: String) = basePathLock.withLock(url) {
        try {
            basePath.removeEntry(fileSystemSafe(url))
        } catch (_: Throwable) {
            // throws when not found
        }
    }

    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) = basePathLock.withLock(oldUrl) {
        basePathLock.withLock(newUrl) {
            val writableFileStream = basePath.resolveUrl(newUrl, true).createWritable()
            try {
                val source = basePath.resolveUrl(oldUrl).getFile()
                writableFileStream.write(source)
                basePath.removeEntry(fileSystemSafe(oldUrl))
                writableFileStream.close()
            } catch (throwable: Throwable) {
                writableFileStream.close()
                basePath.removeEntry(fileSystemSafe(newUrl))
                log.error(throwable) { "could not change media url" }
            }
        }
    }

    // #########################################
    // ############ temporary files ############
    // #########################################

    private suspend fun tmpPath() = basePath.getDirectoryHandle("tmp", jso { create = true })

    private inner class FileBasedOpfsPlatformMediaImpl(
        private val url: String,
        private val file: File,
    ) : OpfsPlatformMedia {
        private val delegate = byteArrayFlowFromReadableStream { file.stream() }

        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): OpfsPlatformMedia =
            OpfsPlatformMediaImpl(url, delegate.let(transformer))

        override suspend fun getTemporaryFile(): Result<OpfsPlatformMedia.TemporaryFile> =
            runCatching {
                basePathLock.withLock(url) {
                    val tmpPath = tmpPath()
                    val fileName = Random.nextString(12)
                    val fileHandle = tmpPath.getFileHandle(fileName, jso { create = true })
                    val writableFileStream = fileHandle.createWritable()
                    try {
                        writableFileStream.write(file)
                        writableFileStream.close()
                    } catch (throwable: Throwable) {
                        writableFileStream.close()
                        tmpPath.removeEntry(fileHandle.name)
                        throw throwable
                    }
                    OpfsPlatformMediaTemporaryFileImpl(fileHandle.getFile(), fileName)
                }
            }

        override suspend fun collect(collector: FlowCollector<ByteArray>) = delegate.collect(collector)
    }

    private inner class OpfsPlatformMediaImpl(
        private val url: String,
        private val delegate: ByteArrayFlow,
    ) : OpfsPlatformMedia, ByteArrayFlow by delegate {
        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): OpfsPlatformMedia =
            OpfsPlatformMediaImpl(url, delegate.let(transformer))

        override suspend fun getTemporaryFile(): Result<OpfsPlatformMedia.TemporaryFile> =
            runCatching {
                basePathLock.withLock(url) {
                    val tmpPath = tmpPath()
                    val fileName = Random.nextString(12)
                    val fileHandle = tmpPath.getFileHandle(fileName, jso { create = true })

                    @Suppress("UNCHECKED_CAST")
                    val writableFileStream = fileHandle.createWritable() as WritableStream<Uint8Array>
                    try {
                        delegate.writeTo(writableFileStream)
                    } catch (throwable: Throwable) {
                        tmpPath.removeEntry(fileHandle.name)
                        throw throwable
                    }
                    OpfsPlatformMediaTemporaryFileImpl(fileHandle.getFile(), fileName)
                }
            }
    }

    private inner class OpfsPlatformMediaTemporaryFileImpl(
        override val file: File,
        private val fileName: String,
    ) : OpfsPlatformMedia.TemporaryFile {
        override suspend fun delete() {
            try {
                val tmpPath = tmpPath()
                tmpPath.removeEntry(fileName)
            } catch (_: Exception) {
            }
        }
    }
}