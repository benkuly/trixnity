package de.connect2x.trixnity.client.media.opfs

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import js.typedarrays.Uint8Array
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestResult
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.utils.toByteArrayFlow
import js.iterable.AsyncIterator
import js.promise.Promise
import js.promise.await
import web.blob.arrayBuffer
import web.fs.*
import web.navigator.navigator
import web.storage.getDirectory
import web.streams.close
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.js
import kotlin.js.toList
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class OpfsMediaStoreTest : TrixnityBaseTest() {

    private data class TestContext(
        val basePath: FileSystemDirectoryHandle,
        val tmpPath: FileSystemDirectoryHandle,
        val cut: OpfsMediaStore,
        val backgroundScope: CoroutineScope,
    )

    private fun test(testBody: suspend TestContext.() -> Unit): TestResult = runTest {
        val basePath = navigator.storage.getDirectory()
        val tmpPath = basePath.getDirectoryHandle("tmp", FileSystemGetDirectoryOptions(create = true))
        val cut = OpfsMediaStore(basePath, backgroundScope, MatrixClientConfiguration(), Clock.System)

        try {
            TestContext(basePath, tmpPath, cut, backgroundScope).testBody()
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
            throw throwable
        } finally {
            basePath.values().toList().forEach { entry ->
                basePath.removeEntry(entry.name, FileSystemRemoveOptions(recursive = true))
            }
        }
    }

    @Test
    fun shouldDeleteAll() = test {
        cut.init(backgroundScope)
        basePath.getFileHandle("url1", FileSystemGetFileOptions(create = true))
        basePath.getFileHandle("url2", FileSystemGetFileOptions(create = true))
        basePath.values().toList().size shouldBe 3
        cut.deleteAll()
        basePath.values().toList().size shouldBe 0
    }

    @Test
    fun shouldAddMedia() = test {
        cut.init(backgroundScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        basePath.values().toList()
        Uint8Array(
            basePath.getFileHandle("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=").getFile().arrayBuffer()
        ).toByteArray().decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldNotAddMediaOnException() = test {
        cut.init(backgroundScope)
        val file = MutableSharedFlow<ByteArray>()
        val job = backgroundScope.launch {
            cut.addMedia("url1", file)
        }
        file.emit("h".encodeToByteArray())
        job.cancelAndJoin()
        basePath.values().toList().size shouldBe 1
    }

    @Test
    fun shouldGetMedia() = test {
        cut.init(backgroundScope)
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
        cut.init(backgroundScope)
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe null
    }

    @Test
    fun shouldDeleteMedia() = test {
        cut.init(backgroundScope)
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
        cut.init(backgroundScope)
        cut.deleteMedia("url1")
        basePath.values().toList().size shouldBe 1
    }

    @Test
    fun shouldChangeMediaUrl() = test {
        cut.init(backgroundScope)
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
        cut.init(backgroundScope)
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
        cut.init(backgroundScope)
        tmpPath.values().toList().size shouldBe 0
    }

    @Test
    fun shouldDeleteTmpDirectoryOnShutdown() = test {
        val opfsJob = Job()
        val opfsScope = CoroutineScope(backgroundScope.coroutineContext + opfsJob)

        cut.init(opfsScope)
        tmpPath.getFileHandle("tmp_file_1", FileSystemGetFileOptions(create = true)).createWritable()
            .apply {
                write("hi".encodeToByteArray().toUint8Array())
                close()
            }
        tmpPath.values().toList().size shouldBe 1
        opfsJob.cancelAndJoin()
        withContext(Dispatchers.Default) {
            delay(500.milliseconds)
        }
        tmpPath.values().toList().size shouldBe 0
    }

    @Test
    fun shouldCreateTemporaryFile() = test {
        cut.init(backgroundScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        tmpPath.values().toList().size shouldBe 0
        val tmpFile = platformMedia.getTemporaryFile().getOrThrow()
        tmpPath.values().toList().size shouldBe 1
        Uint8Array(tmpFile.file.arrayBuffer()).toByteArray().decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldCreateTransformedTemporaryFile() = test {
        cut.init(backgroundScope)
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
        cut.init(backgroundScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        val tmpFile = platformMedia.getTemporaryFile().getOrThrow()
        tmpFile.delete()
        tmpPath.values().toList().size shouldBe 0
    }
}

private suspend fun <V: JsAny?> AsyncIterator<V>.toList(): List<V> = toJsArray(this).await().toList()
private fun <V: JsAny?> toJsArray(self: AsyncIterator<V>): Promise<JsArray<V>> = js("""Array.fromAsync(self)""")
