package net.folivo.trixnity.client.media.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.openDatabase
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.*
import js.typedarrays.Int8Array
import js.typedarrays.asInt8Array
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.media.indexeddb.IndexedDBMediaStore.Companion.MEDIA_OBJECT_STORE_NAME
import net.folivo.trixnity.utils.nextString
import net.folivo.trixnity.utils.toByteArray
import web.blob.Blob
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds

class IndexedDBMediaStoreTest {

    private lateinit var cut: IndexedDBMediaStore
    private lateinit var database: Database

    private val file1 = "file1"
    private val file2 = "file2"

    private suspend fun beforeTest() {
        cut = IndexedDBMediaStore(Random.nextString(22))
        cut.init()
        database = openDatabase(cut.databaseName, 1) { _, _, _ -> }
    }

    private fun afterTest() {
        database.close()
    }

    private fun test(
        testBody: suspend TestScope.() -> Unit
    ): TestResult = runTest(timeout = 5_000.milliseconds) {
        beforeTest()
        testBody()
        afterTest()
    }

    @Test
    fun shouldInit() = test {
        // should not fail
        database.transaction(MEDIA_OBJECT_STORE_NAME) {
            objectStore(MEDIA_OBJECT_STORE_NAME)
        }
    }

    @Test
    fun shouldDeleteAll() = test {
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.put(ByteArray(0).asInt8Array(), Key(file1))
            store.put(ByteArray(0).asInt8Array(), Key(file2))
        }
        database.transaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.getAll().size shouldBe 2
        }
        cut.deleteAll()
        database.transaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.getAll().size shouldBe 0
        }
    }

    @Test
    fun shouldAddMedia() = test {
        cut.addMedia(file1, flowOf("h".toByteArray(), "i".toByteArray()))
        database.transaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.getAll().size shouldBe 1
            store.get(Key(file1)).unsafeCast<Blob>().arrayBuffer()
        }.let { Int8Array(it) }
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
}