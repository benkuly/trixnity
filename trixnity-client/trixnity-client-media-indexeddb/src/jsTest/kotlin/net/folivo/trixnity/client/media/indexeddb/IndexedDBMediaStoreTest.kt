package net.folivo.trixnity.client.media.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.openDatabase
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.*
import js.typedarrays.Int8Array
import js.typedarrays.Uint8Array
import js.typedarrays.asInt8Array
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.media.indexeddb.IndexedDBMediaStore.Companion.MEDIA_OBJECT_STORE_NAME
import net.folivo.trixnity.client.media.indexeddb.IndexedDBMediaStore.Companion.TMP_MEDIA_OBJECT_STORE_NAME
import net.folivo.trixnity.utils.nextString
import net.folivo.trixnity.utils.toByteArray
import net.folivo.trixnity.utils.toByteArrayFlow
import web.blob.Blob
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class IndexedDBMediaStoreTest {

    private lateinit var cut: IndexedDBMediaStore
    private lateinit var database: Database
    private lateinit var coroutineScope: CoroutineScope

    private val file1 = "file1"
    private val file2 = "file2"

    private suspend fun beforeTest() {
        cut = IndexedDBMediaStore(Random.nextString(22))
        coroutineScope = CoroutineScope(Dispatchers.Default)
        cut.init(coroutineScope)
        database = openDatabase(cut.databaseName, 2) { _, _, _ -> }
    }

    private fun afterTest() {
        database.close()
        coroutineScope.cancel()
    }

    private fun test(
        testBody: suspend TestScope.() -> Unit
    ): TestResult = runTest(timeout = 5_000.milliseconds) {
        beforeTest()
        try {
            testBody()
        } finally {
            afterTest()
        }
    }

    @Test
    fun shouldInit() = test {
        // should not fail
        database.transaction(MEDIA_OBJECT_STORE_NAME, TMP_MEDIA_OBJECT_STORE_NAME) {
            objectStore(MEDIA_OBJECT_STORE_NAME)
            objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
        }
    }

    @Test
    fun shouldDeleteAll() = test {
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME, TMP_MEDIA_OBJECT_STORE_NAME) {
            val mediaStore = objectStore(MEDIA_OBJECT_STORE_NAME)
            mediaStore.put(ByteArray(0).asInt8Array(), Key(file1))
            mediaStore.put(ByteArray(0).asInt8Array(), Key(file2))
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.put(ByteArray(0).asInt8Array(), Key(file1))
            tmpStore.put(ByteArray(0).asInt8Array(), Key(file2))
        }
        database.transaction(MEDIA_OBJECT_STORE_NAME, TMP_MEDIA_OBJECT_STORE_NAME) {
            val mediaStore = objectStore(MEDIA_OBJECT_STORE_NAME)
            mediaStore.getAll().size shouldBe 2
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.getAll().size shouldBe 2
        }
        cut.deleteAll()
        database.transaction(MEDIA_OBJECT_STORE_NAME, TMP_MEDIA_OBJECT_STORE_NAME) {
            val mediaStore = objectStore(MEDIA_OBJECT_STORE_NAME)
            mediaStore.getAll().size shouldBe 0
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.getAll().size shouldBe 0
        }
    }

    @Test
    fun shouldAddMedia() = test {
        cut.addMedia(file1, flowOf("h".toByteArray(), "i".toByteArray()))
        database.transaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.getAll().size shouldBe 1
            store.get(Key(file1)).unsafeCast<Blob>()
        }.let { Int8Array(it.arrayBuffer()) }
            .asByteArray().decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldGetMedia() = test {
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.put(Blob(arrayOf("hi".encodeToByteArray().asInt8Array())), Key(file1))
        }
        cut.getMedia(file1)?.toByteArray()?.decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldGetMediaWhenNotExists() = test {
        cut.getMedia(file1)?.toByteArray()?.decodeToString() shouldBe null
    }

    @Test
    fun shouldDeleteMedia() = test {
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.put("hi".encodeToByteArray().asInt8Array(), Key(file1))
        }
        cut.deleteMedia(file1)
        database.transaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.getAll().size shouldBe 0
        }
    }

    @Test
    fun shouldDeleteMediaWhenNotExists() = test {
        // should not fail
        cut.deleteMedia(file1)
    }

    @Test
    fun shouldChangeMediaUrl() = test {
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.put("hi".encodeToByteArray().asInt8Array(), Key(file1))
        }
        cut.changeMediaUrl(file1, file2)
        database.transaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.getAll().size shouldBe 1
            store.get(Key(file2)).unsafeCast<ByteArray?>()?.decodeToString() shouldBe "hi"
        }
    }

    @Test
    fun shouldChangeMediaUrlWhenFileNotExists() = test {
        cut.changeMediaUrl(file1, file2)
        database.transaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.getAll().size shouldBe 0
        }
    }

    @Test
    fun shouldDeleteTmpDatabaseOnStartup() = test {
        database.writeTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.put(ByteArray(0).asInt8Array(), Key(file1))
        }
        database.transaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.getAll().size shouldBe 1
        }
        cut.init(coroutineScope)
        database.transaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.getAll().size shouldBe 0
        }
    }

    @Test
    fun shouldDeleteTmpDirectoryOnShutdown() = test {
        database.writeTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.put(ByteArray(0).asInt8Array(), Key(file1))
        }
        database.transaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.getAll().size shouldBe 1
        }
        coroutineScope.cancel()
        withContext(Dispatchers.Default) {
            delay(50.milliseconds)
        }
        database.transaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.getAll().size shouldBe 0
        }
        cut.init(coroutineScope)
    }

    @Test
    fun shouldCreateTemporaryFile() = test {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        database.transaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.getAll().size shouldBe 0
        }
        val tmpFile = platformMedia.getTemporaryFile().getOrThrow()
        database.transaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.getAll().size shouldBe 1
        }
        Uint8Array(tmpFile.file.arrayBuffer()).toByteArray().decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldCreateTransformedTemporaryFile() = test {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        database.transaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.getAll().size shouldBe 0
        }
        val tmpFile = platformMedia
            .transformByteArrayFlow { "###encrypted###".encodeToByteArray().toByteArrayFlow() }
            .getTemporaryFile()
            .getOrThrow()
        database.transaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.getAll().size shouldBe 1
        }
        Uint8Array(tmpFile.file.arrayBuffer()).toByteArray().decodeToString() shouldBe "###encrypted###"
    }

    @Test
    fun shouldDeleteTemporaryFile() = test {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        val tmpFile = platformMedia.getTemporaryFile().getOrThrow()
        tmpFile.delete()
        database.transaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.getAll().size shouldBe 0
        }
    }
}