package net.folivo.trixnity.client.store.sqldelight

import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.MatrixId.UserId
import kotlin.coroutines.CoroutineContext

class SqlDelightAccountRepository(
    private val db: AccountQueries,
    private val context: CoroutineContext
) : AccountRepository {
    override suspend fun get(key: Long): Account? = withContext(context) {
        db.getAccount(key).executeAsOneOrNull()?.let {
            Account(
                userId = it.user_id?.let { it1 -> UserId(it1) },
                deviceId = it.device_id,
                accessToken = it.access_token,
                syncBatchToken = it.sync_batch_token,
                filterId = it.filter_id
            )
        }
    }

    override suspend fun save(key: Long, value: Account) = withContext(context) {
        db.saveAccount(
            Sql_account(
                id = key,
                user_id = value.userId?.full,
                device_id = value.deviceId,
                access_token = value.accessToken,
                sync_batch_token = value.syncBatchToken,
                filter_id = value.filterId
            )
        )
    }

    override suspend fun delete(key: Long) = withContext(context) {
        db.deleteAccount(key)
    }

}