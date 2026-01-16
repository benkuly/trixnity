package de.connect2x.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThirdPartyIdentifier(
    @SerialName("added_at") val addedAt: Long,
    @SerialName("address") val address: String,
    @SerialName("medium") val medium: Medium,
    @SerialName("validated_at") val validatedAt: Long
) {
    @Serializable
    enum class Medium {
        @SerialName("email")
        EMAIL,

        @SerialName("msisdn")
        MSISDN
    }
}