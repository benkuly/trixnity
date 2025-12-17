package net.folivo.trixnity.client.media.opfs

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import js.iterable.AsyncIterator
import js.iterable.asFlow
import js.iterable.iterator
import js.typedarrays.Uint8Array
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.utils.toByteArrayFlow
import web.blob.arrayBuffer
import web.fs.*
import web.navigator.navigator
import web.storage.getDirectory
import web.streams.close
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class OpfsMediaStoreTest {

    private lateinit var cut: OpfsMediaStore
    lateinit var basePath: FileSystemDirectoryHandle
    lateinit var tmpPath: FileSystemDirectoryHandle
    lateinit var coroutineScope: CoroutineScope

    fun test(testBody: suspend TestScope.() -> Unit): TestResult = runTest {
        basePath = navigator.storage.getDirectory()
        coroutineScope = CoroutineScope(Dispatchers.Default)
        tmpPath = basePath.getDirectoryHandle("tmp", FileSystemGetDirectoryOptions(create = true))
        cut = OpfsMediaStore(basePath, coroutineScope, MatrixClientConfiguration(), Clock.System)
        try {
            testBody()
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            throw throwable
        } finally {
            basePath.values().asFlow().collect { entry ->
                basePath.removeEntry(entry.name, FileSystemRemoveOptions(recursive = true))
            }
            coroutineScope.cancel()
        }
    }

    @Test
    fun shouldDeleteAll() = test {
        cut.init(coroutineScope)
        basePath.getFileHandle("url1", FileSystemGetFileOptions(create = true))
        basePath.getFileHandle("url2", FileSystemGetFileOptions(create = true))
        basePath.values().toList().size shouldBe 3
        cut.deleteAll()
        basePath.values().toList().size shouldBe 0
    }

    @Test
    fun shouldAddMedia() = test {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        basePath.values().toList()
        Uint8Array(
            basePath.getFileHandle("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=").getFile().arrayBuffer()
        ).toByteArray().decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldNotAddMediaOnException() = test {
        cut.init(coroutineScope)
        val file = MutableSharedFlow<ByteArray>()
        val job = async {
            cut.addMedia("url1", file)
        }
        file.emit("h".encodeToByteArray())
        job.cancel()
        basePath.values().toList().size shouldBe 1
    }

    @Test
    fun shouldGetMedia() = test {
        cut.init(coroutineScope)
        basePath.getFileHandle("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=", FileSystemGetFileOptions(create = true))
            .createWritable()
            .apply {
                write("hi".encodeToByteArray().toUint8Array())
                close()
            }
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldGetMediaWhenFileNotExists() = test {
        cut.init(coroutineScope)
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe null
    }

    @Test
    fun shouldDeleteMedia() = test {
        cut.init(coroutineScope)
        basePath.getFileHandle("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=", FileSystemGetFileOptions(create = true))
            .createWritable()
            .apply {
                write("hi".encodeToByteArray().toUint8Array())
                close()
            }
        cut.deleteMedia("url1")
        basePath.values().toList().size shouldBe 1
    }

    @Test
    fun shouldDeleteMediaWhenFileNotExists() = test {
        cut.init(coroutineScope)
        cut.deleteMedia("url1")
        basePath.values().toList().size shouldBe 1
    }

    @Test
    fun shouldChangeMediaUrl() = test {
        cut.init(coroutineScope)
        basePath.getFileHandle("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=", FileSystemGetFileOptions(create = true))
            .createWritable()
            .apply {
                write("hi".encodeToByteArray().toUint8Array())
                close()
            }
        cut.changeMediaUrl("url1", "url2")
        Uint8Array(
            basePath.getFileHandle("hnKdljIEgbx_eKM0uMgfIWYx_slrDvGQQFN8QUQ4QGg=").getFile().arrayBuffer()
        ).toByteArray().decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldChangeMediaUrlWhenFileNotExists() = test {
        cut.init(coroutineScope)
        cut.changeMediaUrl("url1", "url2")
        basePath.values().toList().size shouldBe 1
    }

    @Test
    fun shouldDeleteTmpDirectoryOnStartup() = test {
        tmpPath.getFileHandle("tmp_file_1", FileSystemGetFileOptions(create = true)).createWritable()
            .apply {
                write("hi".encodeToByteArray().toUint8Array())
                close()
            }
        tmpPath.values().toList().size shouldBe 1
        cut.init(coroutineScope)
        tmpPath.values().toList().size shouldBe 0
    }

    @Test
    fun shouldDeleteTmpDirectoryOnShutdown() = test {
        cut.init(coroutineScope)
        tmpPath.getFileHandle("tmp_file_1", FileSystemGetFileOptions(create = true)).createWritable()
            .apply {
                write("hi".encodeToByteArray().toUint8Array())
                close()
            }
        tmpPath.values().toList().size shouldBe 1
        coroutineScope.cancel()
        withContext(Dispatchers.Default) {
            delay(100.milliseconds)
        }
        tmpPath.values().toList().size shouldBe 0
    }

    @Test
    fun shouldCreateTemporaryFile() = test {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        tmpPath.values().toList().size shouldBe 0
        val tmpFile = platformMedia.getTemporaryFile().getOrThrow()
        tmpPath.values().toList().size shouldBe 1
        Uint8Array(tmpFile.file.arrayBuffer()).toByteArray().decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldCreateTransformedTemporaryFile() = test {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        tmpPath.values().toList().size shouldBe 0
        val tmpFile = platformMedia
            .transformByteArrayFlow { "###encrypted###".encodeToByteArray().toByteArrayFlow() }
            .getTemporaryFile()
            .getOrThrow()
        tmpPath.values().toList().size shouldBe 1
        Uint8Array(tmpFile.file.arrayBuffer()).toByteArray().decodeToString() shouldBe "###encrypted###"
    }

    @Test
    fun shouldDeleteTemporaryFile() = test {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        val tmpFile = platformMedia.getTemporaryFile().getOrThrow()
        tmpFile.delete()
        tmpPath.values().toList().size shouldBe 0
    }

    private suspend fun <V> AsyncIterator<V>.toList(): List<V> {
        val list = mutableListOf<V>()
        asFlow().collect { entry ->
            list.add(entry)
        }
        return list.toList()
    }
}