package net.folivo.trixnity.client.store.repository.exposed

import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.upsert

internal object ExposedAccount : LongIdTable("account") {
    val olmPickleKey = text("olm_pickle_key").nullable()
    val baseUrl = text("base_url").nullable()
    val userId = text("user_id").nullable()
    val deviceId = text("device_id").nullable()
    val accessToken = text("access_token").nullable()
    val syncBatchToken = text("sync_batch_token").nullable()
    val filterId = text("filter_id").nullable()
    val backgroundFilterId = text("background_filter_id").nullable()
    val displayName = text("display_name").nullable()
    val avatarUrl = text("avatar_url").nullable()
}

internal class ExposedAccountRepository : AccountRepository {
    override suspend fun get(key: Long): Account? = withExposedRead {
        ExposedAccount.select { ExposedAccount.id eq key }.firstOrNull()?.let {
            Account(
                olmPickleKey = it[ExposedAccount.olmPickleKey],
                baseUrl = it[ExposedAccount.baseUrl],
                userId = it[ExposedAccount.userId]?.let { it1 -> UserId(it1) },
                deviceId = it[ExposedAccount.deviceId],
                accessToken = it[ExposedAccount.accessToken],
                syncBatchToken = it[ExposedAccount.syncBatchToken],
                filterId = it[ExposedAccount.filterId],
                backgroundFilterId = it[ExposedAccount.backgroundFilterId],
                displayName = it[ExposedAccount.displayName],
                avatarUrl = it[ExposedAccount.avatarUrl],
            )
        }
    }

    override suspend fun save(key: Long, value: Account): Unit = withExposedWrite {
        ExposedAccount.upsert {
            it[id] = key
            it[olmPickleKey] = value.olmPickleKey
            it[baseUrl] = value.baseUrl?.toString()
            it[userId] = value.userId?.full
            it[deviceId] = value.deviceId
            it[accessToken] = value.accessToken
            it[syncBatchToken] = value.syncBatchToken
            it[filterId] = value.filterId
            it[backgroundFilterId] = value.backgroundFilterId
            it[displayName] = value.displayName
            it[avatarUrl] = value.avatarUrl?.toString()
        }
    }

    override suspend fun delete(key: Long): Unit = withExposedWrite {
        ExposedAccount.deleteWhere { ExposedAccount.id eq key }
    }

    override suspend fun deleteAll(): Unit = withExposedWrite {
        ExposedAccount.deleteAll()
    }
}

