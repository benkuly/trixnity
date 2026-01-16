@file:OptIn(ExperimentalWasmJsInterop::class)

package de.connect2x.trixnity.client.media.indexeddb

import js.buffer.ArrayBuffer
import js.typedarrays.Uint8Array
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.promise
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.MediaStoreModule
import de.connect2x.trixnity.client.media.CachedMediaStore
import de.connect2x.trixnity.client.media.MediaStore
import de.connect2x.trixnity.idb.utils.IDBUtils
import de.connect2x.trixnity.idb.utils.readTransaction
import de.connect2x.trixnity.idb.utils.writeTransaction
import de.connect2x.trixnity.utils.ByteArrayFlow
import de.connect2x.trixnity.utils.byteArrayFlowFromReadableStream
import de.connect2x.trixnity.utils.nextString
import de.connect2x.trixnity.utils.toByteArray
import de.connect2x.trixnity.utils.writeTo
import org.koin.dsl.module
import web.blob.Blob
import web.events.EventType
import web.events.addEventHandler
import web.http.BodyInit
import web.http.Response
import web.http.blob
import web.idb.IDBDatabase
import web.idb.IDBValidKey
import web.streams.TransformStream
import web.window.window
import kotlin.random.Random
import kotlin.time.Clock

internal class IndexedDBMediaStore(
    private val databaseName: String,
    coroutineScope: CoroutineScope,
    configuration: MatrixClientConfiguration,
    clock: Clock,
) : CachedMediaStore(coroutineScope, configuration, clock) {
    companion object {
        const val MEDIA_OBJECT_STORE_NAME = "media"
        const val TMP_MEDIA_OBJECT_STORE_NAME = "tmp"
    }

    private lateinit var database: IDBDatabase
    override suspend fun init(coroutineScope: CoroutineScope) {
        database = IDBUtils.openDatabase(databaseName, 2) { database, oldVersion, _ ->
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
    
    override suspend fun deleteAllFromStore() {
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME, TMP_MEDIA_OBJECT_STORE_NAME) {
            objectStore(MEDIA_OBJECT_STORE_NAME).clear()
            objectStore(TMP_MEDIA_OBJECT_STORE_NAME).clear()
        }
    }

    override suspend fun addMedia(url: String, content: ByteArrayFlow) = coroutineScope {
        val transformStream = TransformStream<Uint8Array<ArrayBuffer>, Uint8Array<ArrayBuffer>>()
        launch {
            content.writeTo(transformStream.writable)
        }
        val value = Response(BodyInit(transformStream.readable)).blob()
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.put(value, IDBValidKey(url))
        }
    }

    override suspend fun getMedia(url: String): IndexeddbPlatformMedia? =
        database.readTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.get<Blob>(IDBValidKey(url))
        }
            ?.let { BlobBasedIndexeddbPlatformMediaImpl(url, it) }

    override suspend fun deleteMedia(url: String): Unit =
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.delete(IDBValidKey(url))
        }

    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) {
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            val value = store.get<Blob>(IDBValidKey(oldUrl))
            if (value != null) {
                store.put(value, IDBValidKey(newUrl))
                store.delete(IDBValidKey(oldUrl))
            }
        }
    }

    private inner class BlobBasedIndexeddbPlatformMediaImpl(
        private val url: String,
        private val file: Blob,
    ) : IndexeddbPlatformMedia {
        private val delegate = byteArrayFlowFromReadableStream { file.stream() }

        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): IndexeddbPlatformMedia =
            IndexeddbPlatformMediaImpl(url, delegate.let(transformer))

        override suspend fun getTemporaryFile(): Result<IndexeddbPlatformMedia.TemporaryFile> = runCatching {
            getTemporaryFile(file)
        }

        override suspend fun collect(collector: FlowCollector<ByteArray>) = delegate.collect(collector)

        override suspend fun toByteArray(
            coroutineScope: CoroutineScope?,
            expectedSize: Long?,
            maxSize: Long?
        ): ByteArray? =
            toByteArray(url, delegate, coroutineScope, expectedSize, maxSize)
                ?: if (maxSize != null) delegate.toByteArray(maxSize) else delegate.toByteArray()
    }

    private inner class IndexeddbPlatformMediaImpl(
        private val url: String,
        private val delegate: ByteArrayFlow,
    ) : IndexeddbPlatformMedia, ByteArrayFlow by delegate {
        override fun transformByteArrayFlow(transformer: (ByteArrayFlow) -> ByteArrayFlow): IndexeddbPlatformMedia =
            IndexeddbPlatformMediaImpl(url, delegate.let(transformer))

        override suspend fun getTemporaryFile(): Result<IndexeddbPlatformMedia.TemporaryFile> = runCatching {
            coroutineScope {
                val transformStream = TransformStream<Uint8Array<ArrayBuffer>, Uint8Array<ArrayBuffer>>()
                launch {
                    delegate.writeTo(transformStream.writable)
                }
                val file = Response(BodyInit(transformStream.readable)).blob()
                getTemporaryFile(file)
            }
        }

        override suspend fun toByteArray(
            coroutineScope: CoroutineScope?,
            expectedSize: Long?,
            maxSize: Long?
        ): ByteArray? =
            toByteArray(url, delegate, coroutineScope, expectedSize, maxSize)
                ?: if (maxSize != null) delegate.toByteArray(maxSize) else delegate.toByteArray()
    }

    private suspend fun getTemporaryFile(file: Blob): IndexeddbPlatformMediaTemporaryFileImpl {
        val key = Random.nextString(12)
        val blob = database.writeTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            store.put(file, IDBValidKey(key))
            checkNotNull(store.get<Blob>(IDBValidKey(key)))
        }
        return IndexeddbPlatformMediaTemporaryFileImpl(blob, key)
    }

    private inner class IndexeddbPlatformMediaTemporaryFileImpl(
        override val file: Blob,
        private val key: String,
    ) : IndexeddbPlatformMedia.TemporaryFile {
        override suspend fun delete() {
            try {
                database.writeTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
                    val store = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
                    store.delete(IDBValidKey(key))
                }
            } catch (_: Exception) {

            }
        }
    }
}

fun MediaStoreModule.Companion.indexedDB(databaseName: String = "trixnity_media") = MediaStoreModule {
    module {
        single<MediaStore> {
            IndexedDBMediaStore(
                databaseName = databaseName,
                coroutineScope = get(),
                configuration = get(),
                clock = get()
            )
        }
    }
}