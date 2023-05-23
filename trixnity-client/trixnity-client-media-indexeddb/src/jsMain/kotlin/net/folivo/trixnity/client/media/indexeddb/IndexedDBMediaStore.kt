package net.folivo.trixnity.client.media.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.openDatabase
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.toByteArray
import net.folivo.trixnity.utils.toByteArrayFlow
import org.khronos.webgl.Uint8Array

class IndexedDBMediaStore(val databaseName: String = "trixnity_media") : MediaStore {
    companion object {
        const val MEDIA_OBJECT_STORE_NAME = "media"
    }

    private lateinit var database: Database
    override suspend fun init() {
        database = openDatabase(databaseName, 1) { database, oldVersion, _ ->
            when {
                oldVersion < 1 -> {
                    database.createObjectStore(MEDIA_OBJECT_STORE_NAME)
                }
            }
        }
    }

    override suspend fun addMedia(url: String, content: ByteArrayFlow) {
        val value = content.toByteArray().unsafeCast<Uint8Array>()
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.put(value, Key(url))
        }
    }

    override suspend fun getMedia(url: String): ByteArrayFlow? =
        database.transaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.get(Key(url))
        }.unsafeCast<ByteArray?>()?.toByteArrayFlow()

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

    override suspend fun clearCache(): Unit =
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.clear()
        }

    override suspend fun deleteAll(): Unit =
        database.writeTransaction(MEDIA_OBJECT_STORE_NAME) {
            val store = objectStore(MEDIA_OBJECT_STORE_NAME)
            store.clear()
        }
}