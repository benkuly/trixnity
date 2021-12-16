package net.folivo.trixnity.client.store

import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.UserId

class AccountStore(
    private val repository: AccountRepository,
    private val rtm: RepositoryTransactionManager,
    private val storeScope: CoroutineScope
) {
    val baseUrl = MutableStateFlow<Url?>(null)
    val userId = MutableStateFlow<UserId?>(null)
    val deviceId = MutableStateFlow<String?>(null)
    val accessToken = MutableStateFlow<String?>(null)
    val syncBatchToken = MutableStateFlow<String?>(null)
    val filterId = MutableStateFlow<String?>(null)

    suspend fun init() {
        val account = rtm.transaction { repository.get(1) }
        baseUrl.value = account?.baseUrl
        userId.value = account?.userId
        deviceId.value = account?.deviceId
        accessToken.value = account?.accessToken
        syncBatchToken.value = account?.syncBatchToken
        filterId.value = account?.filterId

        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        storeScope.launch(start = UNDISPATCHED) {
            combine(baseUrl, userId, deviceId, accessToken, syncBatchToken, filterId) { values ->
                Account(
                    baseUrl = values[0] as Url?,
                    userId = values[1] as UserId?,
                    deviceId = values[2] as String?,
                    accessToken = values[3] as String?,
                    syncBatchToken = values[4] as String?,
                    filterId = values[5] as String?
                )
            }.collect { rtm.transaction { repository.save(1, it) } }
        }
    }
}