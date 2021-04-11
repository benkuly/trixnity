package net.folivo.trixnity.appservice.rest.api.room

enum class Membership(val value: String) {
    INVITE("invite"),
    JOIN("join"),
    LEAVE("leave"),
    BAN("ban"),
}