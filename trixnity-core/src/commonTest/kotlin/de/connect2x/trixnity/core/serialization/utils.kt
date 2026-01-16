package de.connect2x.trixnity.core.serialization

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }