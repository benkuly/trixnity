package de.connect2x.trixnity.crypto.core

import io.ktor.util.*
import js.typedarrays.Uint8Array
import randomFillSync
import web.crypto.crypto

actual fun fillRandomBytes(array: ByteArray) {
    val buffer = fastToBuffer(array)
    val view = Uint8Array(buffer, 0, array.size)

    if (PlatformUtils.IS_BROWSER) {
        crypto.getRandomValues(view)
    } else {
        randomFillSync(view)
    }

    fastCopyBack(array, buffer)
}