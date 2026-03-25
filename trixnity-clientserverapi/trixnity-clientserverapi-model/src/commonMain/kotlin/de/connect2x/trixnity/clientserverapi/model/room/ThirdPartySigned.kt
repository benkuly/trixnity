package de.connect2x.trixnity.clientserverapi.model.room

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Signed
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ThirdPartySigned = Signed<ThirdParty, String>

@Serializable
data class ThirdParty(
    @SerialName("sender") val sender: UserId,
    @SerialName("mxid") val mxid: UserId,
    @SerialName("token") val token: String,
)
