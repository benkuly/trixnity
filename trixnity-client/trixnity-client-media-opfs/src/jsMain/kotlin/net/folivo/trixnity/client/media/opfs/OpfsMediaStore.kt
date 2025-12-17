package net.folivo.trixnity.client.media.opfs

import io.github.oshai.kotlinlogging.KotlinLogging
import js.buffer.ArrayBuffer
import js.iterable.asFlow
import js.typedarrays.Uint8Array
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.MediaStoreModule
import net.folivo.trixnity.client.media.CachedMediaStore
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.utils.*
import okio.ByteString.Companion.toByteString
import org.koin.dsl.module
import web.events.EventType
import web.events.addEventHandler
import web.file.File
import web.fs.*
import web.streams.WritableStream
import web.streams.close
import web.window.window
import kotlin.random.Random
import kotlin.time.Clock

private val log = KotlinLogging.logger("net.folivo.trixnity.client.media.opfs.OpfsMediaStore")

internal class OpfsMediaStore(
    private val basePath: FileSystemDirectoryHandle,
    coroutineScope: CoroutineScope,
    configuration: MatrixClientConfiguration,
    clock: Clock,
) : CachedMediaStore(coroutineScope, configuration, clock) {

    private val basePathLock = KeyedMutex<String>()
    private suspend fun tmpPath() = basePath.getDirectoryHandle("tmp", FileSystemGetDirectoryOptions(create = true))

    override suspend fun init(coroutineScope: CoroutineScope) {
        suspend fun delTmp() {
            val tmpPath = tmpPath()
            basePath.removeEntry(tmpPath.name, FileSystemRemoveOptions(recursive = true))
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

    override suspend fun deleteAllFromStore() {
        basePath.values().asFlow().collect { entry ->
            basePath.removeEntry(entry.name, FileSystemRemoveOptions(recursive = true))
        }
    }

    private fun fileSystemSafe(url: String) = url.encodeToByteArray().toByteString().sha256().base64Url()

    private suspend fun FileSystemDirectoryHandle.resolveUrl(url: String, create: Boolean = false) =
        getFileHandle(fileSystemSafe(url), FileSystemGetFileOptions(create = create))

    override suspend fun addMedia(url: String, content: ByteArrayFlow) = basePathLock.withLock(url) {
        @Suppress("UNCHECKED_CAST")
        val writableFileStream =
            basePath.resolveUrl(url, true).createWritable() as WritableStream<Uint8Array<ArrayBuffer>>
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
                    val fileHandle = tmpPath.getFileHandle(fileName, FileSystemGetFileOptions(create = true))
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

        override suspend fun toByteArray(
            coroutineScope: CoroutineScope?,
            expectedSize: Long?,
            maxSize: Long?
        ): ByteArray? =
            toByteArray(url, delegate, coroutineScope, expectedSize, maxSize)
                ?: if (maxSize != null) delegate.toByteArray(maxSize) else delegate.toByteArray()
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
                    val fileHandle = tmpPath.getFileHandle(fileName, FileSystemGetFileOptions(create = true))

                    @Suppress("UNCHECKED_CAST")
                    val writableFileStream = fileHandle.createWritable() as WritableStream<Uint8Array<ArrayBuffer>>
                    try {
                        delegate.writeTo(writableFileStream)
                    } catch (throwable: Throwable) {
                        tmpPath.removeEntry(fileHandle.name)
                        throw throwable
                    }
                    OpfsPlatformMediaTemporaryFileImpl(fileHandle.getFile(), fileName)
                }
            }

        override suspend fun toByteArray(
            coroutineScope: CoroutineScope?,
            expectedSize: Long?,
            maxSize: Long?
        ): ByteArray? =
            toByteArray(url, delegate, coroutineScope, expectedSize, maxSize)
                ?: if (maxSize != null) delegate.toByteArray(maxSize) else delegate.toByteArray()
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

fun MediaStoreModule.Companion.opfs(basePath: FileSystemDirectoryHandle) = MediaStoreModule {
    module {
        single<MediaStore> {
            OpfsMediaStore(
                basePath = basePath,
                coroutineScope = get(),
                configuration = get(),
                clock = get()
            )
        }
    }
}