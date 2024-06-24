package net.folivo.trixnity.client.media.opfs

import io.kotest.matchers.shouldBe
import js.iterable.AsyncIterableIterator
import js.objects.jso
import js.typedarrays.Uint8Array
import js.typedarrays.asInt8Array
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.utils.toByteArray
import net.folivo.trixnity.utils.toByteArrayFlow
import web.fs.FileSystemDirectoryHandle
import web.navigator.navigator
import kotlin.test.Test

class OpfsMediaStoreTest {

    lateinit var cut: OpfsMediaStore
    lateinit var basePath: FileSystemDirectoryHandle

    fun runThisTest(testBody: suspend TestScope.() -> Unit): TestResult = runTest {
        basePath = navigator.storage.getDirectory()
        cut = OpfsMediaStore(basePath)
        try {
            testBody()
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
        } finally {
            for (entry in basePath.values()) {
                basePath.removeEntry(entry.name)
            }
        }
    }

    @Test
    fun shouldDeleteAll() = runThisTest {
        cut.init()
        basePath.getFileHandle("url1", jso { create = true })
        basePath.getFileHandle("url2", jso { create = true })
        basePath.values().toList().size shouldBe 2
        cut.deleteAll()
        basePath.values().toList().size shouldBe 0
    }

    @Test
    fun shouldAddMedia() = runThisTest {
        cut.init()
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        basePath.values().toList().forEach { println(it.name + " ") }
        Uint8Array(
            basePath.getFileHandle("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=").getFile().arrayBuffer()
        ).toByteArray().decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldNotAddMediaOnException() = runThisTest {
        cut.init()
        val file = MutableSharedFlow<ByteArray>()
        val job = async {
            cut.addMedia("url1", file)
        }
        file.emit("h".encodeToByteArray())
        job.cancel()
        basePath.values().toList().size shouldBe 0
    }

    @Test
    fun shouldGetMedia() = runThisTest {
        cut.init()
        basePath.getFileHandle("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=").createWritable().apply {
            write("hi".encodeToByteArray().asInt8Array())
        }
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldGetMediaWhenFileNotExists() = runThisTest {
        cut.init()
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe null
    }

    @Test
    fun shouldDeleteMedia() = runThisTest {
        cut.init()
        basePath.getFileHandle("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=").createWritable().apply {
            write("hi".encodeToByteArray().asInt8Array())
        }
        cut.deleteMedia("url1")
        basePath.values().toList().size shouldBe 0
    }

    @Test
    fun shouldDeleteMediaWhenFileNotExists() = runThisTest {
        cut.init()
        cut.deleteMedia("url1")
        basePath.values().toList().size shouldBe 0
    }

    @Test
    fun shouldChangeMediaUrl() = runThisTest {
        cut.init()
        basePath.getFileHandle("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=").createWritable().apply {
            write("hi".encodeToByteArray().asInt8Array())
        }
        cut.changeMediaUrl("url1", "url2")
        Uint8Array(
            basePath.getFileHandle("hnKdljIEgbx_eKM0uMgfIWYx_slrDvGQQFN8QUQ4QGg=").getFile().arrayBuffer()
        ).toByteArray().decodeToString() shouldBe "hi"
    }

    private suspend fun <V> AsyncIterableIterator<V>.toList(): List<V> {
        val list = mutableListOf<V>()
        for (entry in this) {
            list.add(entry)
        }
        return list.toList()
    }
}