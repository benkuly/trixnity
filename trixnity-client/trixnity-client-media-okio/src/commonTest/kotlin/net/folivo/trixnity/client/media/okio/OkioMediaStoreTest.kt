package net.folivo.trixnity.client.media.okio

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.utils.toByteArray
import net.folivo.trixnity.utils.toByteArrayFlow
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class OkioMediaStoreTest {

    lateinit var cut: OkioMediaStore
    lateinit var fileSystem: FakeFileSystem

    private val basePath = "/data/media".toPath()
    private val file1 = "/data/media/file1".toPath()
    private val file2 = "/data/media/file2".toPath()

    @BeforeTest
    fun beforeTest() {
        fileSystem = FakeFileSystem()
        cut = OkioMediaStore(basePath, fileSystem)
    }

    @AfterTest
    fun afterTest() {
        fileSystem.checkNoOpenFiles()
    }

    @Test
    fun shouldInit() = runTest {
        cut.init()
        fileSystem.exists(basePath) shouldBe true
        cut.init() // should not fail
    }

    @Test
    fun shouldDeleteAll() = runTest {
        cut.init()
        fileSystem.write(file1) {}
        fileSystem.write(file2) {}
        fileSystem.listOrNull(basePath)?.size shouldBe 3
        cut.deleteAll()
        fileSystem.listOrNull(basePath)?.size shouldBe 1
    }

    @Test
    fun shouldAddMedia() = runTest {
        cut.init()
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        fileSystem.read(basePath.resolve("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=")) { readUtf8() } shouldBe "hi"
    }

    @Test
    fun shouldNotAddMediaOnException() = runTest {
        cut.init()
        val file = MutableSharedFlow<ByteArray>()
        val job = async {
            cut.addMedia("url1", file)
        }
        file.emit("h".encodeToByteArray())
        job.cancel()
        fileSystem.exists(basePath.resolve("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=")) shouldBe false
    }

    @Test
    fun shouldGetMedia() = runTest {
        cut.init()
        fileSystem.write(basePath.resolve("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=")) {
            writeUtf8("hi")
        }
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldGetMediaWhenFileNotExists() = runTest {
        cut.init()
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe null
    }

    @Test
    fun shouldDeleteMedia() = runTest {
        cut.init()
        fileSystem.write(basePath.resolve("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=")) {
            writeUtf8("hi")
        }
        cut.deleteMedia("url1")
        fileSystem.listOrNull(basePath)?.size shouldBe 1
    }

    @Test
    fun shouldDeleteMediaWhenFileNotExists() = runTest {
        cut.init()
        cut.deleteMedia("url1")
        fileSystem.listOrNull(basePath)?.size shouldBe 1
    }

    @Test
    fun shouldChangeMediaUrl() = runTest {
        cut.init()
        fileSystem.write(basePath.resolve("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=")) {
            writeUtf8("hi")
        }
        cut.changeMediaUrl("url1", "url2")
        fileSystem.read(basePath.resolve("hnKdljIEgbx_eKM0uMgfIWYx_slrDvGQQFN8QUQ4QGg=")) { readUtf8() } shouldBe "hi"
    }

    @Test
    fun shouldChangeMediaUrlWhenFileNotExists() = runTest {
        cut.init()
        cut.changeMediaUrl("url1", "url2")
        fileSystem.listOrNull(basePath)?.size shouldBe 1
    }
}