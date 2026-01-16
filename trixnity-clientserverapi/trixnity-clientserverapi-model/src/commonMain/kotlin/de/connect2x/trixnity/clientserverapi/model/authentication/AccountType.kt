package de.connect2x.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AccountType(val value: String) {
    @SerialName("guest")
    GUEST("guest"),

    @SerialName("user")
    USER("user")
}