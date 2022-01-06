package net.folivo.trixnity.client.store

import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.UserId

class AccountStore(
    private val repository: AccountRepository,
    private val rtm: RepositoryTransactionManager,
    private val storeScope: CoroutineScope
) {
    val olmPickleKey = MutableStateFlow<String?>(null)
    val baseUrl = MutableStateFlow<Url?>(null)
    val userId = MutableStateFlow<UserId?>(null)
    val deviceId = MutableStateFlow<String?>(null)
    val accessToken = MutableStateFlow<String?>(null)
    val syncBatchToken = MutableStateFlow<String?>(null)
    val filterId = MutableStateFlow<String?>(null)
    val displayName = MutableStateFlow<String?>(null)
    val avatarUrl = MutableStateFlow<Url?>(null)

    suspend fun init() {
        val account = rtm.transaction { repository.get(1) }
        olmPickleKey.value = account?.olmPickleKey
        baseUrl.value = account?.baseUrl
        userId.value = account?.userId
        deviceId.value = account?.deviceId
        accessToken.value = account?.accessToken
        syncBatchToken.value = account?.syncBatchToken
        filterId.value = account?.filterId
        displayName.value = account?.displayName
        avatarUrl.value = account?.avatarUrl

        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        storeScope.launch(start = UNDISPATCHED) {
            combine(
                olmPickleKey,
                baseUrl,
                userId,
                deviceId,
                accessToken,
                syncBatchToken,
                filterId,
                displayName,
                avatarUrl
            ) { values ->
                Account(
                    olmPickleKey = values[0] as String?,
                    baseUrl = values[1] as Url?,
                    userId = values[2] as UserId?,
                    deviceId = values[3] as String?,
                    accessToken = values[4] as String?,
                    syncBatchToken = values[5] as String?,
                    filterId = values[6] as String?,
                    displayName = values[7] as String?,
                    avatarUrl = values[8] as Url?,
                )
            }.collect { rtm.transaction { repository.save(1, it) } }
        }
    }
}