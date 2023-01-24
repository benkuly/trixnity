package net.folivo.trixnity.client.store.repository.indexeddb

import com.juul.indexeddb.Database
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.VersionChangeTransaction
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toSet
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToDynamic
import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.client.store.repository.KeyChainLinkRepository
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key

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
        fun VersionChangeTransaction.migrate(database: Database, oldVersion: Int) {
            when {
                oldVersion < 1 -> {
                    database.createObjectStore(
                        objectStoreName,
                        KeyPath(
                            "signingUserId",
                            "signingKeyId",
                            "signingKeyValue",
                            "signedUserId",
                            "signedKeyId",
                            "signedKeyValue"
                        )
                    ).apply {
                        createIndex("signing", KeyPath("signingUserId", "signingKeyId", "signingKeyValue"), false)
                        createIndex("signed", KeyPath("signedUserId", "signedKeyId", "signedKeyValue"), false)
                    }
                }
            }
        }
    }

    override suspend fun save(keyChainLink: KeyChainLink): Unit = withIndexedDBWrite { store ->
        store.put(
            json.encodeToDynamic(
                IndexedDBKeyChainLink(
                    signingUserId = keyChainLink.signingUserId.full,
                    signingKeyId = keyChainLink.signingKey.keyId ?: "",
                    signingKeyValue = keyChainLink.signingKey.value,
                    signedUserId = keyChainLink.signedUserId.full,
                    signedKeyId = keyChainLink.signedKey.keyId ?: "",
                    signedKeyValue = keyChainLink.signedKey.value,
                )
            )
        )
        Unit
    }

    override suspend fun getBySigningKey(signingUserId: UserId, signingKey: Key.Ed25519Key): Set<KeyChainLink> =
        withIndexedDBRead { store ->
            store.index("signing")
                .openCursor(
                    com.juul.indexeddb.Key(signingUserId.full, signingKey.keyId, signingKey.value),
                    autoContinue = true
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
                    com.juul.indexeddb.Key(signedUserId.full, signedKey.keyId, signedKey.value),
                    autoContinue = true
                )
                .collect { store.delete(com.juul.indexeddb.Key(it.primaryKey)) }
        }

    override suspend fun deleteAll(): Unit = withIndexedDBWrite { store ->
        store.clear()
    }
}