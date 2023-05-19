package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.windows.BCRYPT_USE_SYSTEM_PREFERRED_RNG
import platform.windows.BCryptGenRandom

@OptIn(ExperimentalUnsignedTypes::class)
actual fun fillRandomBytes(array: ByteArray) {
    val status = array.asUByteArray().usePinned { pinned ->
        BCryptGenRandom(
            hAlgorithm = null,
            pbBuffer = pinned.addressOf(0),
            cbBuffer = pinned.get().size.convert(),
            dwFlags = BCRYPT_USE_SYSTEM_PREFERRED_RNG
        )
    }
    if (status != 0) error("BCryptGenRandom failed: $status")
}