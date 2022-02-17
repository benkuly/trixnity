package net.folivo.trixnity.core.model.events.m.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Membership(val value: String) {
    @SerialName("invite")
    INVITE("invite"),

    @SerialName("join")
    JOIN("join"),

    @SerialName("knock")
    KNOCK("knock"),

    @SerialName("leave")
    LEAVE("leave"),

    @SerialName("ban")
    BAN("ban")
}