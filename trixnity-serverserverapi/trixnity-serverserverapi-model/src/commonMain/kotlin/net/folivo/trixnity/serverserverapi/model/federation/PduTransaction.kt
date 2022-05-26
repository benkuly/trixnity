package net.folivo.trixnity.serverserverapi.model.federation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.serverserverapi.model.SignedPersistentDataUnit

@Serializable
data class PduTransaction(
    @SerialName("origin")
    val origin: String,
    @SerialName("origin_server_ts")
    val originTimestamp: Long,
    @SerialName("pdus")
    val pdus: List<SignedPersistentDataUnit<*>>
)