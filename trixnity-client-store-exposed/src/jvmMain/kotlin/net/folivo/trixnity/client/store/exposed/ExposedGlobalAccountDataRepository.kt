package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.core.model.events.Event
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select

internal object ExposedGlobalAccountData : Table("global_account_data") {
    val type = varchar("type", length = 65535)
    override val primaryKey = PrimaryKey(type)
    val event = text("event")
}

internal class ExposedGlobalAccountDataRepository(private val json: Json) : GlobalAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event.GlobalAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(key: String): Event.GlobalAccountDataEvent<*>? {
        return ExposedGlobalAccountData.select { ExposedGlobalAccountData.type eq key }.firstOrNull()?.let {
            json.decodeFromString(serializer, it[ExposedGlobalAccountData.event])
        }
    }

    override suspend fun save(key: String, value: Event.GlobalAccountDataEvent<*>) {
        ExposedGlobalAccountData.replace {
            it[type] = key
            it[event] = json.encodeToString(serializer, value)
        }
    }

    override suspend fun delete(key: String) {
        ExposedGlobalAccountData.deleteWhere { ExposedGlobalAccountData.type eq key }
    }
}