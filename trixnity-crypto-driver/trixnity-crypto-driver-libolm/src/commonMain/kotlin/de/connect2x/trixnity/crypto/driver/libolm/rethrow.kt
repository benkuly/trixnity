package de.connect2x.trixnity.crypto.driver.libolm

import de.connect2x.trixnity.crypto.driver.CryptoDriverException
import de.connect2x.trixnity.libolm.OlmLibraryException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
internal inline fun <T> rethrow(crossinline block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return try {
        block()
    } catch (e: OlmLibraryException) {
        throw CryptoDriverException(e)
    }
}