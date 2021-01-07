package net.folivo.matrix.restclient.api.rooms

enum class Membership(val value: String) {
    INVITE("invite"),
    JOIN("join"),
    LEAVE("leave"),
    BAN("ban"),
}