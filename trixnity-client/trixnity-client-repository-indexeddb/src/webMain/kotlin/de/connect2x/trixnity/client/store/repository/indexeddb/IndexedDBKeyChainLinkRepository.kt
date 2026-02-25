package de.connect2x.trixnity.client.store.repository.indexeddb

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toSet
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.KeyChainLink
import de.connect2x.trixnity.client.store.repository.KeyChainLinkRepository
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.idb.utils.KeyPath
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import web.idb.IDBDatabase

@Serializable
data class IndexedDBKeyChainLink(
    val signingUserId: String,
    val signingKeyId: String,
    val signingKeyValue: String,
    val signedUserId: String,
    val signedKeyId: String,
    val signedKeyValue: String,
)

@OptIn(ExperimentalSerializationApi::class)
internal class IndexedDBKeyChainLinkRepository(
    val json: Json,
) : KeyChainLinkRepository, IndexedDBRepository(objectStoreName) {
    companion object {
        const val objectStoreName = "key_chain_link"
        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 1) {
                createObjectStore(
                    database,
                    objectStoreName,
                    KeyPath.Multiple(
                        "signingUserId",
                        "signingKeyId",
                        "signingKeyValue",
                        "signedUserId",
                        "signedKeyId",
                        "signedKeyValue"
                    )
                ).apply {
                    createIndex(
                        "signing",
                        KeyPath.Multiple("signingUserId", "signingKeyId", "signingKeyValue"),
                        false
                    )
                    createIndex(
                        "signed",
                        KeyPath.Multiple("signedUserId", "signedKeyId", "signedKeyValue"),
                        false
                    )
                }
            }
        }
    }

    override suspend fun save(keyChainLink: KeyChainLink): Unit = withIndexedDBWrite { store ->
        store.put(
            json.encodeToDynamic(
                IndexedDBKeyChainLink(
                    signingUserId = keyChainLink.signingUserId.full,
                    signingKeyId = keyChainLink.signingKey.id ?: "",
                    signingKeyValue = keyChainLink.signingKey.value.value,
                    signedUserId = keyChainLink.signedUserId.full,
                    signedKeyId = keyChainLink.signedKey.id ?: "",
                    signedKeyValue = keyChainLink.signedKey.value.value,
                )
            )
        )
    }

    override suspend fun getBySigningKey(signingUserId: UserId, signingKey: Key.Ed25519Key): Set<KeyChainLink> =
        withIndexedDBRead { store ->
            store.index("signing")
                .openCursor(
                    keyOf(signingUserId.full, signingKey.id, signingKey.value.value),
                )
                .mapNotNull { json.decodeFromDynamicNullable<IndexedDBKeyChainLink>(it.value) }
                .map {
                    KeyChainLink(
                        signingUserId = UserId(it.signingUserId),
                        signingKey = Key.Ed25519Key(it.signingKeyId, it.signingKeyValue),
                        signedUserId = UserId(it.signedUserId),
                        signedKey = Key.Ed25519Key(it.signedKeyId, it.signedKeyValue),
                    )
                }
                .toSet()
        }

    override suspend fun deleteBySignedKey(signedUserId: UserId, signedKey: Key.Ed25519Key): Unit =
        withIndexedDBWrite { store ->
            store.index("signed")
                .openKeyCursor(
                    keyOf(signedUserId.full, signedKey.id, signedKey.value.value),
                )
                .collect { store.delete(it) }
        }

    override suspend fun deleteAll(): Unit = withIndexedDBWrite { store ->
        store.clear()
    }
}