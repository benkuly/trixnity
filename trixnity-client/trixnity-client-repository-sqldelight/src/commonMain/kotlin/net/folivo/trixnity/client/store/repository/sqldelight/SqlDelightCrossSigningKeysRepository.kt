package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.repository.CrossSigningKeysRepository
import net.folivo.trixnity.client.store.sqldelight.KeysQueries
import net.folivo.trixnity.core.model.UserId
import kotlin.coroutines.CoroutineContext

class SqlDelightCrossSigningKeysRepository(
    private val db: KeysQueries,
    private val json: Json,
    private val context: CoroutineContext
) : CrossSigningKeysRepository {
    override suspend fun get(key: UserId): Set<StoredCrossSigningKeys>? = withContext(context) {
        db.getCrossSigningKeys(key.full).executeAsOneOrNull()?.let {
            json.decodeFromString<Set<StoredCrossSigningKeys>>(it)
        }
    }

    override suspend fun save(key: UserId, value: Set<StoredCrossSigningKeys>) = withContext(context) {
        db.saveCrossSigningKeys(key.full, json.encodeToString(value))
    }

    override suspend fun delete(key: UserId) = withContext(context) {
        db.deleteCrossSigningKeys(key.full)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllCrossSigningKeys()
    }
}