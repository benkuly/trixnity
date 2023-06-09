package net.folivo.trixnity.crypto.core

import platform.CoreCrypto.*

internal fun checkError(status: Int) {
    val message = when (status) {
        kCCSuccess -> return
        kCCRNGFailure -> "Random number generator failure"
        kCCMemoryFailure -> "Memory allocation failure"
        kCCUnimplemented -> "Function not implemented for the current algorithm"
        kCCAlignmentError -> "Input size was not aligned properly"
        kCCUnspecifiedError -> "An internal error has been detected, but the exact cause is unknown"
        kCCBufferTooSmall -> "Insufficient buffer provided for specified operation"
        kCCOverflow -> "Operation will result in overflow"
        kCCParamError -> "Illegal parameter value"
        kCCDecodeError -> "Input data did not decode or decrypt properly"
        kCCCallSequenceError -> "Function was called in an improper sequence"
        else -> "Unknown error"
    }
    error(message)
}