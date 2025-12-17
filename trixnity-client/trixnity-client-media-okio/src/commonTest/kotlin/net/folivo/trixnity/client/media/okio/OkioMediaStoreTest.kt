package net.folivo.trixnity.client.media.okio

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.utils.toByteArrayFlow
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock

class OkioMediaStoreTest {

    private lateinit var cut: OkioMediaStore
    lateinit var fileSystem: FakeFileSystem
    lateinit var coroutineScope: CoroutineScope

    private val basePath = "/data/media".toPath()
    private val tmpPath = basePath.resolve("tmp")
    private val file1 = "/data/media/file1".toPath()
    private val file2 = "/data/media/file2".toPath()

    @BeforeTest
    fun beforeTest() {
        fileSystem = FakeFileSystem()
        coroutineScope = CoroutineScope(Dispatchers.Default)
        cut = OkioMediaStore(
            basePath = basePath,
            fileSystem = fileSystem,
            coroutineScope = coroutineScope,
            configuration = MatrixClientConfiguration(),
            clock = Clock.System
        )
    }

    @AfterTest
    fun afterTest() {
        fileSystem.checkNoOpenFiles()
        coroutineScope.cancel()
    }

    @Test
    fun shouldInit() = runTest {
        cut.init(coroutineScope)
        fileSystem.exists(basePath) shouldBe true
        fileSystem.exists(tmpPath) shouldBe true
        cut.init(coroutineScope) // should not fail
    }

    @Test
    fun shouldDeleteAll() = runTest {
        cut.init(coroutineScope)
        fileSystem.write(file1) {}
        fileSystem.write(file2) {}
        fileSystem.listOrNull(basePath)?.size shouldBe 4
        cut.deleteAll()
        fileSystem.listOrNull(basePath)?.size shouldBe 2
    }

    @Test
    fun shouldAddMedia() = runTest {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        fileSystem.read(basePath.resolve("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=")) { readUtf8() } shouldBe "hi"
    }

    @Test
    fun shouldNotAddMediaOnException() = runTest {
        cut.init(coroutineScope)
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
        cut.init(coroutineScope)
        fileSystem.write(basePath.resolve("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=")) {
            writeUtf8("hi")
        }
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldGetMediaWhenFileNotExists() = runTest {
        cut.init(coroutineScope)
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe null
    }

    @Test
    fun shouldDeleteMedia() = runTest {
        cut.init(coroutineScope)
        fileSystem.write(basePath.resolve("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=")) {
            writeUtf8("hi")
        }
        cut.deleteMedia("url1")
        fileSystem.listOrNull(basePath)?.size shouldBe 2
    }

    @Test
    fun shouldDeleteMediaWhenFileNotExists() = runTest {
        cut.init(coroutineScope)
        cut.deleteMedia("url1")
        fileSystem.listOrNull(basePath)?.size shouldBe 2
    }

    @Test
    fun shouldChangeMediaUrl() = runTest {
        cut.init(coroutineScope)
        fileSystem.write(basePath.resolve("K5pAaUF5iDoN1BsrFr4kJ0bP8ayM_Q_ftEtyeb_FY2I=")) {
            writeUtf8("hi")
        }
        cut.changeMediaUrl("url1", "url2")
        fileSystem.read(basePath.resolve("hnKdljIEgbx_eKM0uMgfIWYx_slrDvGQQFN8QUQ4QGg=")) { readUtf8() } shouldBe "hi"
    }

    @Test
    fun shouldChangeMediaUrlWhenFileNotExists() = runTest {
        cut.init(coroutineScope)
        cut.changeMediaUrl("url1", "url2")
        fileSystem.listOrNull(basePath)?.size shouldBe 2
    }

    @Test
    fun shouldDeleteTmpDirectoryOnStartup() = runTest {
        fileSystem.createDirectories(tmpPath)
        fileSystem.write(tmpPath.resolve("tmp_file_1")) { writeUtf8("hi") }
        fileSystem.listOrNull(tmpPath)?.size shouldBe 1
        cut.init(coroutineScope)
        fileSystem.listOrNull(tmpPath)?.size shouldBe 0
    }

    @Test
    fun shouldDeleteTmpDirectoryOnShutdown() = runTest {
        cut.init(coroutineScope)
        fileSystem.write(tmpPath.resolve("tmp_file_1")) { writeUtf8("hi") }
        fileSystem.listOrNull(tmpPath)?.size shouldBe 1
        coroutineScope.cancel()
        coroutineScope.coroutineContext.job.join()
        fileSystem.listOrNull(tmpPath)?.size shouldBe null
    }

    @Test
    fun shouldCreateTemporaryFile() = runTest {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        fileSystem.listOrNull(tmpPath)?.size shouldBe 0
        val tmpFile = platformMedia.getTemporaryFile().getOrThrow()
        fileSystem.listOrNull(tmpPath)?.size shouldBe 1
        fileSystem.read(tmpFile.path) { readUtf8() } shouldBe "hi"
    }

    @Test
    fun shouldCreateTransformedTemporaryFile() = runTest {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        fileSystem.listOrNull(tmpPath)?.size shouldBe 0
        val tmpFile = platformMedia
            .transformByteArrayFlow { "###encrypted###".encodeToByteArray().toByteArrayFlow() }
            .getTemporaryFile()
            .getOrThrow()
        fileSystem.listOrNull(tmpPath)?.size shouldBe 1
        fileSystem.read(tmpFile.path) { readUtf8() } shouldBe "###encrypted###"
    }

    @Test
    fun shouldDeleteTemporaryFile() = runTest {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        val tmpFile = platformMedia.getTemporaryFile().getOrThrow()
        tmpFile.delete()
        fileSystem.listOrNull(tmpPath)?.size shouldBe 0
    }
}