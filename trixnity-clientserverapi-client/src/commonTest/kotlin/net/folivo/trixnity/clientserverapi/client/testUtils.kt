package net.folivo.trixnity.clientserverapi.client

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.trim() }