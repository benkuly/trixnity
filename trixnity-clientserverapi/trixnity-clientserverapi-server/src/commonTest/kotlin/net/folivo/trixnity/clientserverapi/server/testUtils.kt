package net.folivo.trixnity.clientserverapi.server

fun String.trimToFlatJson() = this.trimIndent().lines()
    .joinToString("") { it.replace(": ", ":").trim() }