package net.folivo.trixnity.serverserverapi.server

import dev.mokkery.matcher.ArgMatchersScope
import dev.mokkery.matcher.matching
import io.ktor.client.request.*
import io.ktor.http.*

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

fun HttpRequestBuilder.someSignature() =
    header(HttpHeaders.Authorization, """X-Matrix origin=other.hs.host,key="ed25519:key1",sig="sig"""")