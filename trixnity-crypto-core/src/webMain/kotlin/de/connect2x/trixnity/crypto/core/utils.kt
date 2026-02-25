package de.connect2x.trixnity.crypto.core

import js.buffer.ArrayBuffer
import js.core.JsPrimitives.toKotlinByte
import js.typedarrays.Int8Array
import js.typedarrays.Uint8Array
import js.typedarrays.toInt8Array

internal expect fun fastToBuffer(bytes: ByteArray): ArrayBuffer

internal expect fun fastCopyBack(target: ByteArray, buffer: ArrayBuffer)


internal fun ByteArray.fastToUint8Array(): Uint8Array<ArrayBuffer>
        = Uint8Array(fastToBuffer(this), 0, size)

internal fun slowCopyBack(target: ByteArray, buffer: ArrayBuffer) {
    val view = Int8Array(buffer, 0, target.size)

    repeat(target.size) {
        target[it] = view[it].toKotlinByte()
    }
}

internal fun slowToBuffer(bytes: ByteArray) = bytes.toInt8Array().buffer