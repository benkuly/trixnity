package de.connect2x.trixnity.crypto.core

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.*

actual fun fillRandomBytes(array: ByteArray) {
    if (array.isEmpty()) return

    array.usePinned { pinned ->
        checkError(
            CCRandomGenerateBytes(pinned.addressOf(0), array.size.convert())
        )
    }
}