package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.datetime.Instant
import net.folivo.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.upsert
import org.jetbrains.exposed.sql.select

internal object ExposedOlmForgetFallbackKeyAfter : LongIdTable("olm_forget_fallback_key_after") {
    val value = long("value")
}

internal class ExposedOlmForgetFallbackKeyAfterRepository : OlmForgetFallbackKeyAfterRepository {
    override suspend fun get(key: Long): Instant? = withExposedRead {
        ExposedOlmForgetFallbackKeyAfter.select { ExposedOlmForgetFallbackKeyAfter.id eq key }.firstOrNull()
            ?.let { it[ExposedOlmForgetFallbackKeyAfter.value] }
            ?.let { Instant.fromEpochMilliseconds(it) }
    }

    override suspend fun save(key: Long, value: Instant): Unit = withExposedWrite {
        ExposedOlmForgetFallbackKeyAfter.upsert {
            it[ExposedOlmForgetFallbackKeyAfter.id] = key
            it[ExposedOlmForgetFallbackKeyAfter.value] = value.toEpochMilliseconds()
        }
    }

    override suspend fun delete(key: Long): Unit = withExposedWrite {
        ExposedOlmForgetFallbackKeyAfter.deleteWhere { ExposedOlmForgetFallbackKeyAfter.id eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedOlmForgetFallbackKeyAfter.deleteAll()
    }
}