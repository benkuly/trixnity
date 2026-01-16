package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import kotlin.time.Instant

internal object ExposedOlmForgetFallbackKeyAfter : LongIdTable("olm_forget_fallback_key_after") {
    val value = long("value")
}

internal class ExposedOlmForgetFallbackKeyAfterRepository : OlmForgetFallbackKeyAfterRepository {
    override suspend fun get(key: Long): Instant? = withExposedRead {
        ExposedOlmForgetFallbackKeyAfter.selectAll().where { ExposedOlmForgetFallbackKeyAfter.id eq key }.firstOrNull()
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