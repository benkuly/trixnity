package net.folivo.trixnity.clientserverapi.server

import dev.mokkery.matcher.ArgMatchersScope
import dev.mokkery.matcher.matching

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }

inline fun <reified T> ArgMatchersScope.assert(crossinline assertionBlock: (T) -> Unit): T =
    matching(
        toString = { "assert" },
        predicate = {
            try {
                assertionBlock(it)
                true
            } catch (error: AssertionError) {
                false
            }
        },
    )