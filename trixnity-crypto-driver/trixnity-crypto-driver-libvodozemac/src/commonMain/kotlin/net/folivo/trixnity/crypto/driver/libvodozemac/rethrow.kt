package net.folivo.trixnity.crypto.driver.libvodozemac

import net.folivo.trixnity.crypto.driver.CryptoDriverException
import net.folivo.trixnity.vodozemac.VodozemacException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
internal inline fun <T> rethrow(crossinline block: () -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    return try {
        block()
    } catch (e: VodozemacException) {
        throw CryptoDriverException(e)
    }
}