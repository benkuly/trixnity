package net.folivo.trixnity.api.client

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.trim() }