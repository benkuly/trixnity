package net.folivo.trixnity.client.store

import io.ktor.http.*
import net.folivo.trixnity.core.model.UserId

data class Account(
    val baseUrl: Url?,
    val userId: UserId?,
    val deviceId: String?,
    val accessToken: String?,
    val syncBatchToken: String?,
    val filterId: String?,
    val displayName: String?,
    val avatarUrl: Url?,
)