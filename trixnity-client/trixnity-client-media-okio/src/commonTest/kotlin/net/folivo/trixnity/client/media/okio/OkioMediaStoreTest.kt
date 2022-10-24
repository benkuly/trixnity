package net.folivo.trixnity.client.media.okio

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.toByteArray
import net.folivo.trixnity.core.toByteFlow
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
        fileSystem.createDirectories(basePath)
        fileSystem.write(file1) {}
        fileSystem.write(file2) {}
        fileSystem.listOrNull(basePath)?.size shouldBe 2
        cut.deleteAll()
        fileSystem.listOrNull(basePath)?.size shouldBe 0
    }

    @Test
    fun shouldAddMedia() = runTest {
        fileSystem.createDirectories(basePath)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteFlow())
        fileSystem.read(basePath.resolve("dXJsMQ==")) { readUtf8() } shouldBe "hi"
    }

    @Test
    fun shouldGetMedia() = runTest {
        fileSystem.createDirectories(basePath)
        fileSystem.write(basePath.resolve("dXJsMQ==")) { writeUtf8("hi") }
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldGetMediaWhenFileNotExists() = runTest {
        fileSystem.createDirectories(basePath)
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe null
    }

    @Test
    fun shouldDeleteMedia() = runTest {
        fileSystem.createDirectories(basePath)
        fileSystem.write(basePath.resolve("dXJsMQ==")) { writeUtf8("hi") }
        cut.deleteMedia("url1")
        fileSystem.listOrNull(basePath)?.size shouldBe 0
    }

    @Test
    fun shouldDeleteMediaWhenFileNotExists() = runTest {
        fileSystem.createDirectories(basePath)
        cut.deleteMedia("url1")
        fileSystem.listOrNull(basePath)?.size shouldBe 0
    }

    @Test
    fun shouldChangeMediaUrl() = runTest {
        fileSystem.createDirectories(basePath)
        fileSystem.write(basePath.resolve("dXJsMQ==")) { writeUtf8("hi") }
        cut.changeMediaUrl("url1", "url2")
        fileSystem.read(basePath.resolve("dXJsMg==")) { readUtf8() } shouldBe "hi"
    }
}