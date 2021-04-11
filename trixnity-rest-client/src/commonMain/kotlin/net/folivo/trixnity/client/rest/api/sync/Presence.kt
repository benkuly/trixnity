package net.folivo.trixnity.appservice.rest.api.sync

enum class Presence(val value: String) {
    OFFLINE("offline"),
    ONLINE("online"),
    UNAVAILABLE("unavailable"),
}