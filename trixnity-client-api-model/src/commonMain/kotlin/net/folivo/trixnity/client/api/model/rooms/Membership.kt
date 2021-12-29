package net.folivo.trixnity.client.api.model.rooms

enum class Membership(val value: String) {
    INVITE("invite"),
    JOIN("join"),
    LEAVE("leave"),
    BAN("ban"),
}