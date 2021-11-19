package net.folivo.trixnity.client.store

import net.folivo.trixnity.core.model.UserId

data class Account(
    val userId: UserId?,
    val deviceId: String?,
    val accessToken: String?,
    val syncBatchToken: String?,
    val filterId: String?
)