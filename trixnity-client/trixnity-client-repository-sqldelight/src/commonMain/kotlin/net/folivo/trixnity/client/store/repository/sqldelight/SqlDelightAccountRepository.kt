package net.folivo.trixnity.client.store.repository.sqldelight

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.client.store.sqldelight.AccountQueries
import net.folivo.trixnity.client.store.sqldelight.Sql_account
import net.folivo.trixnity.core.model.UserId
import kotlin.coroutines.CoroutineContext

class SqlDelightAccountRepository(
    private val db: AccountQueries,
    private val context: CoroutineContext
) : AccountRepository {
    override suspend fun get(key: Long): Account? = withContext(context) {
        db.getAccount(key).executeAsOneOrNull()?.let {
            Account(
                olmPickleKey = it.olm_pickle_key,
                baseUrl = it.base_url,
                userId = it.user_id?.let { it1 -> UserId(it1) },
                deviceId = it.device_id,
                accessToken = it.access_token,
                syncBatchToken = it.sync_batch_token,
                filterId = it.filter_id,
                backgroundFilterId = it.background_filter_id,
                displayName = it.display_name,
                avatarUrl = it.avatar_url,
            )
        }
    }

    override suspend fun save(key: Long, value: Account) = withContext(context) {
        db.saveAccount(
            Sql_account(
                id = key,
                olm_pickle_key = value.olmPickleKey,
                base_url = value.baseUrl,
                user_id = value.userId?.full,
                device_id = value.deviceId,
                access_token = value.accessToken,
                sync_batch_token = value.syncBatchToken,
                filter_id = value.filterId,
                background_filter_id = value.backgroundFilterId,
                display_name = value.displayName,
                avatar_url = value.avatarUrl,
            )
        )
    }

    override suspend fun delete(key: Long) = withContext(context) {
        db.deleteAccount(key)
    }

    override suspend fun deleteAll() = withContext(context) {
        db.deleteAllAccounts()
    }
}