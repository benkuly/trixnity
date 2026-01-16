package de.connect2x.trixnity.client.store

import kotlinx.serialization.Serializable
import de.connect2x.trixnity.clientserverapi.client.LogoutInfo

@Serializable
data class Authentication(
    val providerId: String,
    val providerData: String,
    val logoutInfo: LogoutInfo?,
)