package de.connect2x.trixnity.clientserverapi.server

import dev.mokkery.matcher.MokkeryMatcherScope
import dev.mokkery.matcher.matches

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }

inline fun <reified T> MokkeryMatcherScope.assert(crossinline assertionBlock: (T) -> Unit): T =
    matches(
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