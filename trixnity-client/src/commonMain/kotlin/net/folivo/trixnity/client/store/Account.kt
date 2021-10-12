package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.MatrixId

data class Account(
    val userId: MatrixId.UserId?,
    val deviceId: String?,
    val accessToken: String?,
    val syncBatchToken: String?,
    val filterId: String?
)