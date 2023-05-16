package net.folivo.trixnity.crypto.core

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array

fun ArrayBuffer.toByteArray(): ByteArray = Int8Array(this).toByteArray()

fun Int8Array.toByteArray(): ByteArray = this.unsafeCast<ByteArray>()

fun ByteArray.toInt8Array(): Int8Array = this.unsafeCast<Int8Array>()