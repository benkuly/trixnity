package net.folivo.trixnity.client.media.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.openDatabase
import js.buffer.ArrayBuffer
import js.typedarrays.Uint8Array
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.media.CachedMediaStore
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.media.PlatformMedia
import net.folivo.trixnity.utils.*
import org.koin.dsl.module
import web.blob.Blob
import web.events.EventType
import web.events.addEventHandler
import web.http.BodyInit
import web.http.Response
import web.streams.TransformStream
import web.window.window
import kotlin.random.Random

@Deprecated("switch to createIndexedDBMediaStoreModule", ReplaceWith("createIndexedDBMediaStoreModule(databaseName)"))
class IndexedDBMediaStore(
    val databaseName: String = "trixnity_media",
    private val toByteArray: (suspend (uri: String, media: ByteArrayFlow, coroutineScope: CoroutineScope?, expectedSize: Long?, maxSize: Long?) -> ByteArray?)? = null,
) : MediaStore {
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
        val transformStream = TransformStream<Uint8Array<ArrayBuffer>, Uint8Array<ArrayBuffer>>()
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
            ?.let { BlobBasedIndexeddbPlatformMediaImpl(url, it) }

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
            toByteArray?.invoke(url, delegate, coroutineScope, expectedSize, maxSize)
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
            toByteArray?.invoke(url, delegate, coroutineScope, expectedSize, maxSize)
                ?: if (maxSize != null) delegate.toByteArray(maxSize) else delegate.toByteArray()
    }

    private suspend fun getTemporaryFile(file: Blob): IndexeddbPlatformMediaTemporaryFileImpl {
        val key = Random.nextString(12)
        val blob = database.writeTransaction(TMP_MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(TMP_MEDIA_OBJECT_STORE_NAME)
            store.put(file, Key(key))
            store.get(Key(key))
        }.unsafeCast<Blob>()
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
                    store.delete(Key(key))
                }
            } catch (_: Exception) {

            }
        }
    }
}

internal class IndexedDBCachedMediaStore(
    databaseName: String = "trixnity_media",
    coroutineScope: CoroutineScope,
    configuration: MatrixClientConfiguration,
    clock: Clock,
) : CachedMediaStore(coroutineScope, configuration, clock) {
    private val delegate = IndexedDBMediaStore(databaseName, ::toByteArray)

    override suspend fun init(coroutineScope: CoroutineScope) = delegate.init(coroutineScope)
    override suspend fun addMedia(url: String, content: ByteArrayFlow) = delegate.addMedia(url, content)
    override suspend fun getMedia(url: String): PlatformMedia? = delegate.getMedia(url)
    override suspend fun deleteMedia(url: String) = delegate.deleteMedia(url)
    override suspend fun changeMediaUrl(oldUrl: String, newUrl: String) = delegate.changeMediaUrl(oldUrl, newUrl)
    override suspend fun clearCache() = delegate.clearCache()
    override suspend fun deleteAll() = delegate.deleteAll()
}

fun createIndexedDBMediaStoreModule(databaseName: String = "trixnity_media") = module {
    single<MediaStore> {
        IndexedDBCachedMediaStore(
            databaseName = databaseName,
            coroutineScope = get(),
            configuration = get(),
            clock = get()
        )
    }
}