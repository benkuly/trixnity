package net.folivo.trixnity.client.rest.api.room

enum class Membership(val value: String) {
    INVITE("invite"),
    JOIN("join"),
    LEAVE("leave"),
    BAN("ban"),
}