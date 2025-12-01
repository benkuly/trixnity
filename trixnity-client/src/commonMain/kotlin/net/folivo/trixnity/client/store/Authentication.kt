package net.folivo.trixnity.client.store

import kotlinx.serialization.Serializable
import net.folivo.trixnity.clientserverapi.client.LogoutInfo

@Serializable
data class Authentication(
    val providerId: String,
    val providerData: String,
    val logoutInfo: LogoutInfo?,
)