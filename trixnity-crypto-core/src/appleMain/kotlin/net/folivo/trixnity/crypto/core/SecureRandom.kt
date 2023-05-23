package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.*

actual fun fillRandomBytes(array: ByteArray) {
    if (array.isEmpty()) return
    
    val size = array.size
    val status = array.usePinned { pinned ->
        CCRandomGenerateBytes(pinned.addressOf(0), size.convert())
    }
    checkStatus(status)
}

fun checkStatus(status: CCRNGStatus) {
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
    error("CCRandomGenerateBytes failed[status=$status]: $message")
}