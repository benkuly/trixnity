package net.folivo.trixnity.client.store.repository.exposed

import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.UserId
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
    val filterId = text("filter_id").nullable()
    val backgroundFilterId = text("background_filter_id").nullable()
    val displayName = text("display_name").nullable()
    val avatarUrl = text("avatar_url").nullable()
    val isLocked = bool("is_locked").nullable()
}

internal class ExposedAccountRepository : AccountRepository {
    override suspend fun get(key: Long): Account? = withExposedRead {
        ExposedAccount.selectAll().where { ExposedAccount.id eq key }.firstOrNull()?.let {
            Account(
                olmPickleKey = it[ExposedAccount.olmPickleKey]
                    ?: throw IllegalStateException("olmPickleKey not found"),
                baseUrl = it[ExposedAccount.baseUrl] ?: throw IllegalStateException("baseUrl not found"),
                userId = it[ExposedAccount.userId]?.let { it1 -> UserId(it1) }
                    ?: throw IllegalStateException("userId not found"),
                deviceId = it[ExposedAccount.deviceId] ?: throw IllegalStateException("deviceId not found"),
                accessToken = it[ExposedAccount.accessToken],
                refreshToken = it[ExposedAccount.refreshToken],
                syncBatchToken = it[ExposedAccount.syncBatchToken],
                filterId = it[ExposedAccount.filterId],
                backgroundFilterId = it[ExposedAccount.backgroundFilterId],
                displayName = it[ExposedAccount.displayName],
                avatarUrl = it[ExposedAccount.avatarUrl],
                isLocked = it[ExposedAccount.isLocked] ?: false,
            )
        }
    }

    override suspend fun save(key: Long, value: Account): Unit = withExposedWrite {
        ExposedAccount.upsert {
            it[id] = key
            it[olmPickleKey] = value.olmPickleKey
            it[baseUrl] = value.baseUrl
            it[userId] = value.userId.full
            it[deviceId] = value.deviceId
            it[accessToken] = value.accessToken
            it[refreshToken] = value.refreshToken
            it[syncBatchToken] = value.syncBatchToken
            it[filterId] = value.filterId
            it[backgroundFilterId] = value.backgroundFilterId
            it[displayName] = value.displayName
            it[avatarUrl] = value.avatarUrl
            it[isLocked] = value.isLocked
        }
    }

    override suspend fun delete(key: Long): Unit = withExposedWrite {
        ExposedAccount.deleteWhere { ExposedAccount.id eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedAccount.deleteAll()
    }
}

