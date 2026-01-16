package de.connect2x.trixnity.core.model.events.m

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// TODO support unknown
@Serializable
enum class Presence(val value: String) {
    @SerialName("online")
    ONLINE("online"),

    @SerialName("offline")
    OFFLINE("offline"),

    @SerialName("unavailable")
    UNAVAILABLE("unavailable")
}