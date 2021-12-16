package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import kotlin.coroutines.CoroutineContext

class SqlDelightGlobalAccountDataRepository(
    private val db: GlobalAccountDataQueries,
    private val json: Json,
    private val context: CoroutineContext,
) : GlobalAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(GlobalAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(key: String): GlobalAccountDataEvent<*>? = withContext(context) {
        db.getGlobalAccountData(key).executeAsOneOrNull()?.let {
            json.decodeFromString(serializer, it.event)
        }
    }

    override suspend fun save(key: String, value: GlobalAccountDataEvent<*>) = withContext(context) {
        db.saveGlobalAccountData(key, json.encodeToString(serializer, value))
    }

    override suspend fun delete(key: String) = withContext(context) {
        db.deleteGlobalAccountData(key)
    }

}