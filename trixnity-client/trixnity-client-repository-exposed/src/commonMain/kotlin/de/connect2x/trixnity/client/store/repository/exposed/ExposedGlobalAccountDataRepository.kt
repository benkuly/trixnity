package de.connect2x.trixnity.client.store.repository.exposed

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.store.repository.GlobalAccountDataRepository
import de.connect2x.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
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
    private val serializer = json.serializersModule.getContextual(GlobalAccountDataEvent::class)
        ?: throw IllegalArgumentException("could not find event serializer")

    override suspend fun get(firstKey: String): Map<String, GlobalAccountDataEvent<*>> = withExposedRead {
        ExposedGlobalAccountData.selectAll().where { ExposedGlobalAccountData.type.eq(firstKey) }.associate {
            it[ExposedGlobalAccountData.key] to json.decodeFromString(serializer, it[ExposedGlobalAccountData.event])
        }
    }

    override suspend fun get(firstKey: String, secondKey: String): GlobalAccountDataEvent<*>? =
        withExposedRead {
            ExposedGlobalAccountData.selectAll().where {
                ExposedGlobalAccountData.type.eq(firstKey) and
                        ExposedGlobalAccountData.key.eq(secondKey)
            }.firstOrNull()?.let {
                json.decodeFromString(serializer, it[ExposedGlobalAccountData.event])
            }
        }

    override suspend fun save(
        firstKey: String,
        secondKey: String,
        value: GlobalAccountDataEvent<*>
    ): Unit = withExposedWrite {
        ExposedGlobalAccountData.upsert {
            it[type] = firstKey
            it[key] = secondKey
            it[event] = json.encodeToString(serializer, value)
        }
    }

    override suspend fun delete(firstKey: String, secondKey: String): Unit = withExposedWrite {
        ExposedGlobalAccountData.deleteWhere {
            type.eq(firstKey) and
                    key.eq(secondKey)
        }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedGlobalAccountData.deleteAll()
    }
}