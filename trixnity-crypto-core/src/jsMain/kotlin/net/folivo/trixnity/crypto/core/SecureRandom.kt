package net.folivo.trixnity.crypto.core

import crypto
import io.ktor.util.*
import randomFillSync

actual fun fillRandomBytes(array: ByteArray) {
    if (PlatformUtils.IS_BROWSER) {
        crypto.getRandomValues(array.toInt8Array())
    } else {
        randomFillSync(array.toInt8Array())
    }
}