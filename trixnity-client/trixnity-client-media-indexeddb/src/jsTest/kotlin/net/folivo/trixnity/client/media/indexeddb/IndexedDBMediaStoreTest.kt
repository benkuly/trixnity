package net.folivo.trixnity.client.media.indexeddb

import com.benasher44.uuid.uuid4
import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.openDatabase
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.media.indexeddb.IndexedDBMediaStore.Companion.MEDIA_OBJECT_STORE_NAME
import net.folivo.trixnity.utils.toByteArray
import net.folivo.trixnity.utils.toByteArrayFlow
import org.khronos.webgl.Uint8Array
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IndexedDBMediaStoreTest {

    private lateinit var cut: IndexedDBMediaStore
    private lateinit var database: Database

    private val file1 = "file1"
    private val file2 = "file2"

    private suspend fun beforeTest() {
        cut = IndexedDBMediaStore(uuid4().toString())
        cut.init()
        database = openDatabase(cut.databaseName, 1) { _, _, _ -> }
    }

    private fun afterTest() {
        database.close()
    }

    private fun test(
        testBody: suspend TestScope.() -> Unit
    ): TestResult = runTest(dispatchTimeoutMs = 5_000) {
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
            store.put(ByteArray(0).unsafeCast<Uint8Array>(), Key(file1))
            store.put(ByteArray(0).unsafeCast<Uint8Array>(), Key(file2))
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
        cut.addMedia(file1, "hi".encodeToByteArray().toByteArrayFlow())
        database.transaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.getAll().size shouldBe 1
            store.get(Key(file1)).unsafeCast<ByteArray>().decodeToString() shouldBe "hi"
        }
    }

    @Test
    fun shouldGetMedia() = test {
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.put("hi".encodeToByteArray().unsafeCast<Uint8Array>(), Key(file1))
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
            store.put("hi".encodeToByteArray().unsafeCast<Uint8Array>(), Key(file1))
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
            store.put("hi".encodeToByteArray().unsafeCast<Uint8Array>(), Key(file1))
        }
        cut.changeMediaUrl(file1, file2)
        database.transaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.getAll().size shouldBe 1
            store.get(Key(file2)).unsafeCast<ByteArray?>()?.decodeToString() shouldBe "hi"
        }
    }
}