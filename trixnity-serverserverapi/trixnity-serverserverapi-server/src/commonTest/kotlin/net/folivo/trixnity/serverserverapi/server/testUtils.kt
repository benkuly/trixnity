package net.folivo.trixnity.serverserverapi.server

import io.ktor.client.request.*
import io.ktor.http.*
import org.kodein.mock.ArgConstraint
import org.kodein.mock.ArgConstraintsBuilder

fun String.trimToFlatJson() = this.trimIndent().lines().joinToString("") { it.replace(": ", ":").trim() }

inline fun <reified T> ArgConstraintsBuilder.assert(crossinline assertionBlock: (T) -> Unit): T =
    isValid(null, { "assert" }) {
        try {
            assertionBlock(it)
            ArgConstraint.Result.Success
        } catch (error: AssertionError) {
            ArgConstraint.Result.Failure { error.message ?: "" }
        }
    }

fun HttpRequestBuilder.someSignature() =
    header(HttpHeaders.Authorization, """X-Matrix origin=other.hs.host,key="ed25519:key1",sig="sig"""")