package de.connect2x.trixnity.client.store

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface KeyVerificationState {
    val keyValue: String

    /**
     * This key has been verified.
     */
    @Serializable
    @SerialName("verified")
    data class Verified(override val keyValue: String) : KeyVerificationState

    /**
     * This key has been blocked.
     */
    @Serializable
    @SerialName("blocked")
    data class Blocked(override val keyValue: String) : KeyVerificationState
}