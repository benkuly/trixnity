package net.folivo.trixnity.client.store.exposed

import io.ktor.http.*
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.replace
import org.jetbrains.exposed.sql.select

internal object ExposedAccount : LongIdTable("account") {
    val baseUrl = text("base_url").nullable()
    val userId = text("user_id").nullable()
    val deviceId = text("device_id").nullable()
    val accessToken = text("access_token").nullable()
    val syncBatchToken = text("sync_batch_token").nullable()
    val filterId = text("filter_id").nullable()
    val displayName = text("display_name").nullable()
    val avatarUrl = text("avatar_url").nullable()
}

internal class ExposedAccountRepository : AccountRepository {
    override suspend fun get(key: Long): Account? {
        return ExposedAccount.select { ExposedAccount.id eq key }.firstOrNull()?.let {
            Account(
                baseUrl = it[ExposedAccount.baseUrl]?.let { it1 -> Url(it1) },
                userId = it[ExposedAccount.userId]?.let { it1 -> UserId(it1) },
                deviceId = it[ExposedAccount.deviceId],
                accessToken = it[ExposedAccount.accessToken],
                syncBatchToken = it[ExposedAccount.syncBatchToken],
                filterId = it[ExposedAccount.filterId],
                displayName = it[ExposedAccount.displayName],
                avatarUrl = it[ExposedAccount.avatarUrl]?.let { it1 -> Url(it1) },
            )
        }
    }

    override suspend fun save(key: Long, value: Account) {
        ExposedAccount.replace {
            it[id] = key
            it[baseUrl] = value.baseUrl?.toString()
            it[userId] = value.userId?.full
            it[deviceId] = value.deviceId
            it[accessToken] = value.accessToken
            it[syncBatchToken] = value.syncBatchToken
            it[filterId] = value.filterId
            it[displayName] = value.displayName
            it[avatarUrl] = value.avatarUrl?.toString()
        }
    }

    override suspend fun delete(key: Long) {
        ExposedAccount.deleteWhere { ExposedAccount.id eq key }
    }
}