package net.folivo.trixnity.client.store.repository.realm

import io.realm.kotlin.TypedRealm
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
}

internal class RealmAccountRepository : AccountRepository {
    override suspend fun get(key: Long): Account? = withRealmRead {
        findByKey(key).find()?.let { realmAccount ->
            Account(
                olmPickleKey = realmAccount.olmPickleKey,
                baseUrl = realmAccount.baseUrl,
                userId = realmAccount.userId?.let { UserId(it) },
                deviceId = realmAccount.deviceId,
                accessToken = realmAccount.accessToken,
                syncBatchToken = realmAccount.syncBatchToken,
                filterId = realmAccount.filterId,
                backgroundFilterId = realmAccount.backgroundFilterId,
                displayName = realmAccount.displayName,
                avatarUrl = realmAccount.avatarUrl,
            )
        }
    }

    override suspend fun save(key: Long, value: Account) = withRealmWrite {
        val existing = findByKey(key).find()
        val upsert = (existing ?: RealmAccount().apply { id = key }).apply {
            olmPickleKey = value.olmPickleKey
            baseUrl = value.baseUrl
            userId = value.userId?.full
            deviceId = value.deviceId
            accessToken = value.accessToken
            syncBatchToken = value.syncBatchToken
            filterId = value.filterId
            backgroundFilterId = value.backgroundFilterId
            displayName = value.displayName
            avatarUrl = value.avatarUrl
        }
        if (existing == null) {
            copyToRealm(upsert)
        }
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