package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.store.repository.AccountRepository
import net.folivo.trixnity.core.model.MatrixId

class AccountStore(
    private val repository: AccountRepository,
    private val storeScope: CoroutineScope
) {
    val userId = MutableStateFlow<MatrixId.UserId?>(null)
    val deviceId = MutableStateFlow<String?>(null)
    val accessToken = MutableStateFlow<String?>(null)
    val syncBatchToken = MutableStateFlow<String?>(null)
    val filterId = MutableStateFlow<String?>(null)

    suspend fun init() {
        val account = repository.get(1)
        userId.value = account?.userId
        deviceId.value = account?.deviceId
        accessToken.value = account?.accessToken
        syncBatchToken.value = account?.syncBatchToken
        filterId.value = account?.filterId

        storeScope.launch {
            combine(userId, deviceId, accessToken, syncBatchToken, filterId) { u, d, a, s, f ->
                Account(u, d, a, s, f)
            }.collect { repository.save(1, it) }
        }
    }
}