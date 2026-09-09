package de.connect2x.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Membership {
    @SerialName("invite") INVITE,
    @SerialName("join") JOIN,
    @SerialName("knock") KNOCK,
    @SerialName("leave") LEAVE,
    @SerialName("ban") BAN,
}
