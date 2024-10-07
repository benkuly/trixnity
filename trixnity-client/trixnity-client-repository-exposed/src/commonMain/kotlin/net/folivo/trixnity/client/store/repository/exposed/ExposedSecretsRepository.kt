package net.folivo.trixnity.client.store.repository.exposed

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.client.store.repository.SecretsRepository
import net.folivo.trixnity.crypto.SecretType
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

internal object ExposedSecrets : LongIdTable("secrets") {
    val value = text("value")
}

internal class ExposedSecretsRepository(private val json: Json) : SecretsRepository {
    override suspend fun get(key: Long): Map<SecretType, StoredSecret>? = withExposedRead {
        ExposedSecrets.selectAll().where { ExposedSecrets.id eq key }.firstOrNull()?.let {
            it[ExposedSecrets.value].let { outdated -> json.decodeFromString(outdated) }
        }
    }

    override suspend fun save(key: Long, value: Map<SecretType, StoredSecret>): Unit = withExposedWrite {
        ExposedSecrets.upsert {
            it[id] = key
            it[ExposedSecrets.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: Long): Unit = withExposedWrite {
        ExposedSecrets.deleteWhere { ExposedSecrets.id eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedSecrets.deleteAll()
    }
}