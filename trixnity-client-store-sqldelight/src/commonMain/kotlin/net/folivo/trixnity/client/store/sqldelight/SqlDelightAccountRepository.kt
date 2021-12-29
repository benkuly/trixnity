package net.folivo.trixnity.client.store.sqldelight

import io.ktor.http.*
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.UserId
import kotlin.coroutines.CoroutineContext

class SqlDelightAccountRepository(
    private val db: AccountQueries,
    private val context: CoroutineContext
) : AccountRepository {
    override suspend fun get(key: Long): Account? = withContext(context) {
        db.getAccount(key).executeAsOneOrNull()?.let {
            Account(
                baseUrl = it.base_url?.let { it1 -> Url(it1) },
                userId = it.user_id?.let { it1 -> UserId(it1) },
                deviceId = it.device_id,
                accessToken = it.access_token,
                syncBatchToken = it.sync_batch_token,
                filterId = it.filter_id,
                displayName = it.display_name,
                avatarUrl = it.avatar_url?.let { it1 -> Url(it1) },
            )
        }
    }

    override suspend fun save(key: Long, value: Account) = withContext(context) {
        db.saveAccount(
            Sql_account(
                id = key,
                base_url = value.baseUrl?.toString(),
                user_id = value.userId?.full,
                device_id = value.deviceId,
                access_token = value.accessToken,
                sync_batch_token = value.syncBatchToken,
                filter_id = value.filterId,
                display_name = value.displayName,
                avatar_url = value.avatarUrl?.toString(),
            )
        )
    }

    override suspend fun delete(key: Long) = withContext(context) {
        db.deleteAccount(key)
    }

}