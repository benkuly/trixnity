package net.folivo.trixnity.client.api.sync

import net.folivo.trixnity.core.model.MatrixId.UserId

interface SyncBatchTokenStore {
    suspend fun getBatchToken(userId: UserId? = null): String?
    suspend fun setBatchToken(value: String?, userId: UserId? = null)
}