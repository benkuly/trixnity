package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.core.model.events.Event
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

internal object ExposedGlobalAccountData : Table("global_account_data") {
    val type = varchar("type", length = 255)
    val key = varchar("key", length = 255)
    override val primaryKey = PrimaryKey(type, key)
    val event = text("event")
}

internal class ExposedGlobalAccountDataRepository(private val json: Json) : GlobalAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer = json.serializersModule.getContextual(Event.GlobalAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(key: String): Map<String, Event.GlobalAccountDataEvent<*>> {
        return ExposedGlobalAccountData.select { ExposedGlobalAccountData.type.eq(key) }.associate {
            it[ExposedGlobalAccountData.key] to json.decodeFromString(serializer, it[ExposedGlobalAccountData.event])
        }
    }

    override suspend fun save(key: String, value: Map<String, Event.GlobalAccountDataEvent<*>>) {
        ExposedGlobalAccountData.batchReplace(value.entries) { (secondKey, event) ->
            this[ExposedGlobalAccountData.type] = key
            this[ExposedGlobalAccountData.key] = secondKey
            this[ExposedGlobalAccountData.event] = json.encodeToString(serializer, event)
        }
    }

    override suspend fun delete(key: String) {
        ExposedGlobalAccountData.deleteWhere { ExposedGlobalAccountData.type eq key }
    }

    override suspend fun getBySecondKey(firstKey: String, secondKey: String): Event.GlobalAccountDataEvent<*>? {
        return ExposedGlobalAccountData.select {
            ExposedGlobalAccountData.type.eq(firstKey) and
                    ExposedGlobalAccountData.key.eq(secondKey)
        }.firstOrNull()?.let {
            json.decodeFromString(serializer, it[ExposedGlobalAccountData.event])
        }
    }

    override suspend fun saveBySecondKey(firstKey: String, secondKey: String, value: Event.GlobalAccountDataEvent<*>) {
        ExposedGlobalAccountData.replace {
            it[this.type] = firstKey
            it[this.key] = secondKey
            it[this.event] = json.encodeToString(serializer, value)
        }
    }

    override suspend fun deleteBySecondKey(firstKey: String, secondKey: String) {
        ExposedGlobalAccountData.deleteWhere {
            ExposedGlobalAccountData.type.eq(firstKey) and
                    ExposedGlobalAccountData.key.eq(secondKey)
        }
    }

    override suspend fun deleteAll() {
        ExposedGlobalAccountData.deleteAll()
    }
}