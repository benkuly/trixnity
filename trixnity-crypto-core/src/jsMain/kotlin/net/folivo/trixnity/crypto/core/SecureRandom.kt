package net.folivo.trixnity.crypto.core

import io.ktor.util.*
import js.typedarrays.asInt8Array
import randomFillSync
import web.crypto.crypto

actual fun fillRandomBytes(array: ByteArray) {
    if (PlatformUtils.IS_BROWSER) {
        crypto.getRandomValues(array.asInt8Array())
    } else {
        randomFillSync(array.asInt8Array())
    }
}