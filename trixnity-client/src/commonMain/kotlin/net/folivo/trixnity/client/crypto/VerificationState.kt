package net.folivo.trixnity.client.crypto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class VerificationState {
    @Serializable
    @SerialName("verified")
    object Verified : VerificationState()

    @Serializable
    @SerialName("valid")
    object Valid : VerificationState()

    @Serializable
    @SerialName("blocked")
    object Blocked : VerificationState()

    @Serializable
    @SerialName("invalid")
    data class Invalid(val reason: String) : VerificationState()
}
