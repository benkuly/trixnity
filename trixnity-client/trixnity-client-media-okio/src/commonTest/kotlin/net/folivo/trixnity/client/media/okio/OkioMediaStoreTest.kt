package net.folivo.trixnity.client.media.okio

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.core.toByteArray
import net.folivo.trixnity.core.toByteArrayFlow
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        fileSystem.read(basePath.resolve("2b9a40694179883a0dd41b2b16be242746cff1ac8cfd0fdfb44b7279bfc56362")) { readUtf8() } shouldBe "hi"
    }

    @Test
    fun shouldGetMedia() = runTest {
        fileSystem.createDirectories(basePath)
        fileSystem.write(basePath.resolve("2b9a40694179883a0dd41b2b16be242746cff1ac8cfd0fdfb44b7279bfc56362")) {
            writeUtf8(
                "hi"
            )
        }
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldGetMediaWhenFileNotExists() = runTest {
        fileSystem.createDirectories(basePath)
        cut.getMedia("url1")?.toByteArray()?.decodeToString() shouldBe null
    }


    @Test
    fun shouldGetMediaAsSoonAsAddedAndNotBefore() = runTest {
        cut = OkioMediaStore(basePath, fileSystem, testScheduler)
        fileSystem.createDirectories(basePath)
        val emittedH = MutableStateFlow(false)
        val mediaFlow = flow {
            emit("h".encodeToByteArray())
            emittedH.value = true
            delay(1.seconds)
            emit("i".encodeToByteArray())
        }
        launch {
            cut.addMedia("url1", mediaFlow)
        }
        emittedH.first { it }
        val getMediaResult = async {
            cut.getMedia("url1")?.toByteArray()?.decodeToString()
        }
        testScheduler.advanceTimeBy(500.milliseconds)
        getMediaResult.isCompleted shouldBe false
        testScheduler.advanceTimeBy(501.milliseconds)
        getMediaResult.isCompleted shouldBe true
        getMediaResult.await() shouldBe "hi"
    }

    @Test
    fun shouldDeleteMedia() = runTest {
        fileSystem.createDirectories(basePath)
        fileSystem.write(basePath.resolve("2b9a40694179883a0dd41b2b16be242746cff1ac8cfd0fdfb44b7279bfc56362")) {
            writeUtf8(
                "hi"
            )
        }
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
        fileSystem.write(basePath.resolve("2b9a40694179883a0dd41b2b16be242746cff1ac8cfd0fdfb44b7279bfc56362")) {
            writeUtf8(
                "hi"
            )
        }
        cut.changeMediaUrl("url1", "url2")
        fileSystem.read(basePath.resolve("86729d96320481bc7f78a334b8c81f216631fec96b0ef19040537c4144384068")) { readUtf8() } shouldBe "hi"
    }
}