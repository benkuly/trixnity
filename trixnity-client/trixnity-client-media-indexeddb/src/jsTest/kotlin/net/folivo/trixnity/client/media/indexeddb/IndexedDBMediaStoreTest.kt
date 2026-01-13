@file:OptIn(ExperimentalWasmJsInterop::class)

package net.folivo.trixnity.client.media.indexeddb

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.toByteArray
import js.errors.toThrowable
import js.typedarrays.Int8Array
import js.typedarrays.Uint8Array
import js.typedarrays.asByteArray
import js.typedarrays.asInt8Array
import js.typedarrays.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.media.indexeddb.IndexedDBMediaStore.Companion.MEDIA_OBJECT_STORE_NAME
import net.folivo.trixnity.client.media.indexeddb.IndexedDBMediaStore.Companion.TMP_MEDIA_OBJECT_STORE_NAME
import net.folivo.trixnity.idb.utils.IDBUtils
import net.folivo.trixnity.idb.utils.WrappedObjectStore
import net.folivo.trixnity.idb.utils.readTransaction
import net.folivo.trixnity.idb.utils.writeTransaction
import net.folivo.trixnity.utils.nextString
import net.folivo.trixnity.utils.toByteArrayFlow
import web.blob.Blob
import web.blob.arrayBuffer
import web.events.EventHandler
import web.idb.IDBDatabase
import web.idb.IDBValidKey
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class IndexedDBMediaStoreTest {

    private lateinit var cut: IndexedDBMediaStore
    private lateinit var database: IDBDatabase
    private lateinit var coroutineScope: CoroutineScope

    private val file1 = "file1"
    private val file2 = "file2"

    private suspend fun beforeTest() {
        coroutineScope = CoroutineScope(Dispatchers.Default)
        val databaseName = Random.nextString(12)
        cut = IndexedDBMediaStore(databaseName, coroutineScope, MatrixClientConfiguration(), Clock.System)
        cut.init(coroutineScope)
        database = IDBUtils.openDatabase(databaseName, 2) { _, _, _ -> }
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
        database.readTransaction(MEDIA_OBJECT_STORE_NAME, TMP_MEDIA_OBJECT_STORE_NAME) {
            objectStore(MEDIA_OBJECT_STORE_NAME)
            objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
        }
    }

    private suspend infix fun WrappedObjectStore.shouldBeSize(expectedSize: Int) {
        val actualSize = suspendCoroutine { continuation ->
            val request = store.getAll()

            request.onsuccess = EventHandler { event ->
                continuation.resume(event.target.result.size)
            }

            request.onerror = EventHandler { event ->
                continuation.resumeWithException(
                    Error(
                        "get size",
                        event.target.error?.toThrowable()
                    )
                )
            }
        }

        actualSize shouldBe expectedSize
    }

    @Test
    fun shouldDeleteAll() = test {
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME, TMP_MEDIA_OBJECT_STORE_NAME) {
            val mediaStore = objectStore(MEDIA_OBJECT_STORE_NAME)
            mediaStore.put(ByteArray(0).asInt8Array(), IDBValidKey(file1))
            mediaStore.put(ByteArray(0).asInt8Array(), IDBValidKey(file2))
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.put(ByteArray(0).asInt8Array(), IDBValidKey(file1))
            tmpStore.put(ByteArray(0).asInt8Array(), IDBValidKey(file2))
        }
        database.readTransaction(MEDIA_OBJECT_STORE_NAME, TMP_MEDIA_OBJECT_STORE_NAME) {
            val mediaStore = objectStore(MEDIA_OBJECT_STORE_NAME)
            mediaStore shouldBeSize 2
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore shouldBeSize 2
        }
        cut.deleteAll()
        database.readTransaction(MEDIA_OBJECT_STORE_NAME, TMP_MEDIA_OBJECT_STORE_NAME) {
            val mediaStore = objectStore(MEDIA_OBJECT_STORE_NAME)
            mediaStore shouldBeSize 0
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore shouldBeSize 0
        }
    }

    @Test
    fun shouldAddMedia() = test {
        cut.addMedia(file1, flowOf("h".toByteArray(), "i".toByteArray()))
        database.readTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store shouldBeSize 1
            checkNotNull(store.get<Blob>(IDBValidKey(file1)))
        }.let { Int8Array(it.arrayBuffer()) }
            .asByteArray().decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldGetMedia() = test {
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.put(
                Blob(arrayOf("hi".encodeToByteArray().asInt8Array())),
                IDBValidKey(file1)
            )
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
            store.put("hi".encodeToByteArray().asInt8Array(), IDBValidKey(file1))
        }
        cut.deleteMedia(file1)
        database.readTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store shouldBeSize 0
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
            store.put("hi".encodeToByteArray().asInt8Array(), IDBValidKey(file1))
        }
        cut.changeMediaUrl(file1, file2)
        database.readTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store shouldBeSize 1
            store.get<ByteArray>(IDBValidKey(file2))?.decodeToString() shouldBe "hi"
        }
    }

    @Test
    fun shouldChangeMediaUrlWhenFileNotExists() = test {
        cut.changeMediaUrl(file1, file2)
        database.readTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store shouldBeSize 0
        }
    }

    @Test
    fun shouldDeleteTmpDatabaseOnStartup() = test {
        database.writeTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.put(ByteArray(0).asInt8Array(), IDBValidKey(file1))
        }
        database.readTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore shouldBeSize 1
        }
        cut.init(coroutineScope)
        database.readTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore shouldBeSize 0
        }
    }

    @Test
    fun shouldDeleteTmpDirectoryOnShutdown() = test {
        database.writeTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore.put(ByteArray(0).asInt8Array(), IDBValidKey(file1))
        }
        database.readTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore shouldBeSize 1
        }
        coroutineScope.cancel()
        withContext(Dispatchers.Default) {
            delay(50.milliseconds)
        }
        database.readTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore shouldBeSize 0
        }
        cut.init(coroutineScope)
    }

    @Test
    fun shouldCreateTemporaryFile() = test {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        database.readTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore shouldBeSize 0
        }
        val tmpFile = platformMedia.getTemporaryFile().getOrThrow()
        database.readTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore shouldBeSize 1
        }
        Uint8Array(tmpFile.file.arrayBuffer()).toByteArray().decodeToString() shouldBe "hi"
    }

    @Test
    fun shouldCreateTransformedTemporaryFile() = test {
        cut.init(coroutineScope)
        cut.addMedia("url1", "hi".encodeToByteArray().toByteArrayFlow())
        val platformMedia = cut.getMedia("url1").shouldNotBeNull()
        database.readTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore shouldBeSize 0
        }
        val tmpFile = platformMedia
            .transformByteArrayFlow { "###encrypted###".encodeToByteArray().toByteArrayFlow() }
            .getTemporaryFile()
            .getOrThrow()
        database.readTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore shouldBeSize 1
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
        database.readTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val tmpStore = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            tmpStore shouldBeSize 0
        }
    }
}