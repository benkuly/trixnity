package net.folivo.trixnity.client.store.exposed

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.AllowedSecretType
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.client.store.repository.SecretsRepository
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select

internal object ExposedSecrets : LongIdTable("secrets") {
    val value = text("value")
}

internal class ExposedSecretsRepository(private val json: Json) : SecretsRepository {
    override suspend fun get(key: Long): Map<AllowedSecretType, StoredSecret>? {
        return ExposedSecrets.select { ExposedSecrets.id eq key }.firstOrNull()?.let {
            it[ExposedSecrets.value].let { outdated -> json.decodeFromString(outdated) }
        }
    }

    override suspend fun save(key: Long, value: Map<AllowedSecretType, StoredSecret>) {
        ExposedSecrets.replace {
            it[id] = key
            it[this.value] = json.encodeToString(value)
        }
    }

    override suspend fun delete(key: Long) {
        ExposedSecrets.deleteWhere { ExposedSecrets.id eq key }
    }
}