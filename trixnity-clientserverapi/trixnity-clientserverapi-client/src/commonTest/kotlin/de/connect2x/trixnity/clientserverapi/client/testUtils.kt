package de.connect2x.trixnity.clientserverapi.client

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }