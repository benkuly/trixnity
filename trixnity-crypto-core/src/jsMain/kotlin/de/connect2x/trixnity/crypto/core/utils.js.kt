package de.connect2x.trixnity.crypto.core

import js.buffer.ArrayBuffer
import js.typedarrays.Int8Array
import js.typedarrays.asInt8Array

internal actual fun fastToBuffer(bytes: ByteArray): ArrayBuffer
    = bytes.asInt8Array().unsafeCast<Int8Array<ArrayBuffer>>().buffer

internal actual fun fastCopyBack(target: ByteArray, buffer: ArrayBuffer) {
    if (fastToBuffer(target) == buffer) return

    slowCopyBack(target, buffer)
}