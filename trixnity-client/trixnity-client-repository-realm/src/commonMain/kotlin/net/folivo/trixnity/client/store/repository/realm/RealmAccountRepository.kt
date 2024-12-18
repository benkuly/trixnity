package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.UserId

internal class RealmAccount : RealmObject {
    @PrimaryKey
    var id: Long = 0
    var olmPickleKey: String? = null
    var baseUrl: String? = null
    var userId: String? = null
    var deviceId: String? = null
    var accessToken: String? = null
    var syncBatchToken: String? = null
    var filterId: String? = null
    var backgroundFilterId: String? = null
    var displayName: String? = null
    var avatarUrl: String? = null
    var isLocked: Boolean = false
}

internal class RealmAccountRepository : AccountRepository {
    override suspend fun get(key: Long): Account? = withRealmRead {
        findByKey(key).find()?.copyFromRealm()?.let { realmAccount ->
            Account(
                olmPickleKey = realmAccount.olmPickleKey ?: throw IllegalStateException("olmPickleKey not found"),
                baseUrl = realmAccount.baseUrl ?: throw IllegalStateException("baseUrl not found"),
                userId = realmAccount.userId?.let { UserId(it) } ?: throw IllegalStateException("userId not found"),
                deviceId = realmAccount.deviceId ?: throw IllegalStateException("deviceId not found"),
                accessToken = realmAccount.accessToken,
                syncBatchToken = realmAccount.syncBatchToken,
                filterId = realmAccount.filterId,
                backgroundFilterId = realmAccount.backgroundFilterId,
                displayName = realmAccount.displayName,
                avatarUrl = realmAccount.avatarUrl,
                isLocked = realmAccount.isLocked
            )
        }
    }

    override suspend fun save(key: Long, value: Account): Unit = withRealmWrite {
        copyToRealm(
            RealmAccount().apply {
                id = key
                olmPickleKey = value.olmPickleKey
                baseUrl = value.baseUrl
                userId = value.userId.full
                deviceId = value.deviceId
                accessToken = value.accessToken
                syncBatchToken = value.syncBatchToken
                filterId = value.filterId
                backgroundFilterId = value.backgroundFilterId
                displayName = value.displayName
                avatarUrl = value.avatarUrl
                isLocked = value.isLocked
            },
            UpdatePolicy.ALL
        )
    }

    override suspend fun delete(key: Long) = withRealmWrite {
        val existing = findByKey(key)
        delete(existing)
    }

    override suspend fun deleteAll() = withRealmWrite {
        val existing = query<RealmAccount>().find()
        delete(existing)
    }

    private fun TypedRealm.findByKey(key: Long) = query<RealmAccount>("id == $0", key).first()
}