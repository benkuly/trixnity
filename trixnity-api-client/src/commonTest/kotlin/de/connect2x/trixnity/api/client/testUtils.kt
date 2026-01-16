package de.connect2x.trixnity.api.client

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }