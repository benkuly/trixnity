package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.Account
import de.connect2x.trixnity.client.store.repository.AccountRepository
import de.connect2x.trixnity.core.model.UserId
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

internal object ExposedAccount : LongIdTable("account") {
    val olmPickleKey = text("olm_pickle_key").nullable()
    val baseUrl = text("base_url").nullable()
    val userId = text("user_id").nullable()
    val deviceId = text("device_id").nullable()
    val accessToken = text("access_token").nullable()
    val refreshToken = text("refresh_token").nullable()
    val syncBatchToken = text("sync_batch_token").nullable()
    val filter = text("filter").nullable()
    val profile = text("profile").nullable()
}

internal class ExposedAccountRepository(private val json: Json) : AccountRepository {
    override suspend fun get(key: Long): Account? = withExposedRead {
        ExposedAccount.selectAll().where { ExposedAccount.id eq key }.firstOrNull()?.let {
            Account(
                olmPickleKey = it[ExposedAccount.olmPickleKey],
                baseUrl = it[ExposedAccount.baseUrl],
                userId = it[ExposedAccount.userId]?.let { it1 -> UserId(it1) }
                    ?: throw IllegalStateException("userId not found"),
                deviceId = it[ExposedAccount.deviceId] ?: throw IllegalStateException("deviceId not found"),
                accessToken = it[ExposedAccount.accessToken],
                refreshToken = it[ExposedAccount.refreshToken],
                syncBatchToken = it[ExposedAccount.syncBatchToken],
                filter = it[ExposedAccount.filter]?.let { json.decodeFromString(it) },
                profile = it[ExposedAccount.profile]?.let { json.decodeFromString(it) },
            )
        }
    }

    override suspend fun save(key: Long, value: Account): Unit = withExposedWrite {
        @Suppress("DEPRECATION")
        ExposedAccount.upsert {
            it[id] = key
            it[olmPickleKey] = value.olmPickleKey
            it[baseUrl] = value.baseUrl
            it[userId] = value.userId.full
            it[deviceId] = value.deviceId
            it[accessToken] = value.accessToken
            it[refreshToken] = value.refreshToken
            it[syncBatchToken] = value.syncBatchToken
            it[filter] = value.filter?.let { json.encodeToString(it) }
            it[profile] = value.profile?.let { json.encodeToString(it) }
        }
    }

    override suspend fun delete(key: Long): Unit = withExposedWrite {
        ExposedAccount.deleteWhere { ExposedAccount.id eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedAccount.deleteAll()
    }
}

