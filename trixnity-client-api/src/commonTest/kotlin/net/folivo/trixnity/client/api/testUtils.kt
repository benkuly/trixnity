package net.folivo.trixnity.client.api

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.trim() }