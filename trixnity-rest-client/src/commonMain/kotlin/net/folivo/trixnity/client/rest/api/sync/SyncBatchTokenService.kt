package net.folivo.trixnity.appservice.rest.api.sync

import net.folivo.trixnity.core.model.MatrixId.UserId

interface SyncBatchTokenService {
    suspend fun getBatchToken(userId: UserId? = null): String?
    suspend fun setBatchToken(value: String?, userId: UserId? = null)
}