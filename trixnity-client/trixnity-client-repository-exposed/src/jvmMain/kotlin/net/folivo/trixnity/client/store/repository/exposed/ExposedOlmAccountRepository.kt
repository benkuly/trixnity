package net.folivo.trixnity.client.store.repository.exposed

import net.folivo.trixnity.client.store.repository.OlmAccountRepository
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select

internal object ExposedOlmAccount : LongIdTable("olm_account") {
    val pickled = text("pickled")
}

internal class ExposedOlmAccountRepository : OlmAccountRepository {
    override suspend fun get(key: Long): String? {
        return ExposedOlmAccount.select { ExposedOlmAccount.id eq key }.firstOrNull()
            ?.let { it[ExposedOlmAccount.pickled] }
    }

    override suspend fun save(key: Long, value: String) {
        ExposedOlmAccount.replace {
            it[ExposedOlmAccount.id] = key
            it[pickled] = value
        }
    }

    override suspend fun delete(key: Long) {
        ExposedOlmAccount.deleteWhere { ExposedOlmAccount.id eq key }
    }

    override suspend fun deleteAll() {
        ExposedOlmAccount.deleteAll()
    }
}