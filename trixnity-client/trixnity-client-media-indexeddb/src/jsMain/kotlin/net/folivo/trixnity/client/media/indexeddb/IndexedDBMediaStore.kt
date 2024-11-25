package net.folivo.trixnity.client.media.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.openDatabase
import js.typedarrays.Uint8Array
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.byteArrayFlowFromReadableStream
import net.folivo.trixnity.utils.nextString
import net.folivo.trixnity.utils.writeTo
import web.blob.Blob
import web.events.EventType
import web.events.addEventHandler
import web.http.BodyInit
import web.http.Response
import web.streams.TransformStream
import web.window.window
import kotlin.random.Random

class IndexedDBMediaStore(val databaseName: String = "trixnity_media") : MediaStore {
    companion object {
        const val MEDIA_OBJECT_STORE_NAME = "media"
        const val TMP_MEDIA_OBJECT_STORE_NAME = "tmp"
    }

    private lateinit var database: Database
    override suspend fun init(coroutineScope: CoroutineScope) {
        database = openDatabase(databaseName, 2) { database, oldVersion, _ ->
            if (oldVersion < 1) {
                database.createObjectStore(MEDIA_OBJECT_STORE_NAME)
            }

            if (oldVersion < 2) {
                database.createObjectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            }
        }
        suspend fun clearTmp() {
            database.writeTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
                objectStore(TMP_MEDIA_OBJECT_STORE_NAME).clear()
            }
        }
        clearTmp()
        coroutineScope.coroutineContext.job.invokeOnCompletion {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.promise { clearTmp() }
        }
        window.addEventHandler(EventType("unload")) {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.promise { clearTmp() }
        }
    }

    override suspend fun clearCache() = deleteAll()

    override suspend fun deleteAll() {
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME, TMP_MEDIA_OBJECT_STORE_NAME) {
            objectStore(MEDIA_OBJECT_STORE_NAME).clear()
            objectStore(TMP_MEDIA_OBJECT_STORE_NAME).clear()
        }
    }

    override suspend fun addMedia(url: String, content: ByteArrayFlow) = coroutineScope {
        val transformStream = TransformStream<Uint8Array, Uint8Array>()
        launch {
            content.writeTo(transformStream.writable)
        }
        val value = Response(BodyInit(transformStream.readable)).blob()
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.put(value, Key(url))
        }
    }

    override suspend fun getMedia(url: String): IndexeddbPlatformMedia? =
        database.transaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.get(Key(url))
        }.unsafeCast<Blob?>()
            ?.let { BlobBasedIndexeddbPlatformMediaImpl(it) }

    override suspend fun deleteMedia(url: String): Unit =
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.delete(Key(url))
        }

    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) {
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            val value = store.get(Key(oldUrl))
            if ((value == null).not()) { // because value != null is true for undefined
                store.put(value, Key(newUrl))
                store.delete(Key(oldUrl))
            }
        }
    }

    // #########################################
    // ############ temporary files ############
    // #########################################

    private inner class BlobBasedIndexeddbPlatformMediaImpl(
        private val file: Blob,
    ) : IndexeddbPlatformMedia {
        private val delegate = byteArrayFlowFromReadableStream { file.stream() }

        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): IndexeddbPlatformMedia =
            IndexeddbPlatformMediaImpl(delegate.let(transformer))

        override suspend fun getTemporaryFile(): Result<IndexeddbPlatformMedia.TemporaryFile> = runCatching {
            val key = Random.nextString(12)
            val blob = database.writeTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
                val store = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
                store.put(file, Key(key))
                store.get(Key(key))
            }.unsafeCast<Blob>()
            IndexeddbPlatformMediaTemporaryFileImpl(blob, key)
        }

        override suspend fun collect(collector: FlowCollector<ByteArray>) = delegate.collect(collector)
    }

    private inner class IndexeddbPlatformMediaImpl(
        private val delegate: ByteArrayFlow,
    ) : IndexeddbPlatformMedia, ByteArrayFlow by delegate {
        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): IndexeddbPlatformMedia =
            IndexeddbPlatformMediaImpl(delegate.let(transformer))

        override suspend fun getTemporaryFile(): Result<IndexeddbPlatformMedia.TemporaryFile> = runCatching {
            coroutineScope {
                val transformStream = TransformStream<Uint8Array, Uint8Array>()
                launch {
                    delegate.writeTo(transformStream.writable)
                }
                val file = Response(BodyInit(transformStream.readable)).blob()
                val key = Random.nextString(12)
                val blob = database.writeTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
                    val store = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
                    store.put(file, Key(key))
                    store.get(Key(key))
                }.unsafeCast<Blob>()
                IndexeddbPlatformMediaTemporaryFileImpl(blob, key)
            }
        }
    }

    private inner class IndexeddbPlatformMediaTemporaryFileImpl(
        override val file: Blob,
        private val key: String,
    ) : IndexeddbPlatformMedia.TemporaryFile {
        override suspend fun delete() {
            try {
                database.writeTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
                    val store = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
                    store.delete(Key(key))
                }
            } catch (_: Exception) {

            }
        }
    }
}