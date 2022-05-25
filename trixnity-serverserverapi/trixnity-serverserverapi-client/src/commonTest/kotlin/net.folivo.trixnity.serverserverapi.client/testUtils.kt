package net.folivo.trixnity.serverserverapi.client

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }