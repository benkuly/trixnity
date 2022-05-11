package net.folivo.trixnity.serverserverapi.model

import kotlinx.serialization.SerialName
import net.folivo.trixnity.core.model.keys.Signed

data class RequestAuthenticationBody(
    @SerialName("method") val method: String,
    @SerialName("uri") val uri: String,
    @SerialName("origin") val origin: String,
    @SerialName("destination") val destination: String,
    @SerialName("content") val content: String,
)

typealias SignedRequestAuthenticationBody = Signed<RequestAuthenticationBody, String>
