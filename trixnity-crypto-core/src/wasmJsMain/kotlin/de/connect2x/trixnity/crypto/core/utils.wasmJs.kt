package de.connect2x.trixnity.crypto.core

import js.buffer.ArrayBuffer

internal actual fun fastToBuffer(bytes: ByteArray): ArrayBuffer
    = slowToBuffer(bytes)

internal actual fun fastCopyBack(target: ByteArray, buffer: ArrayBuffer)
    = slowCopyBack(target, buffer)