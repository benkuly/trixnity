package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.*

private val almostEmptyArray = ByteArray(1).pin()

@OptIn(ExperimentalUnsignedTypes::class)
private val almostEmptyUArray = UByteArray(1).pin()
internal fun ByteArray.refToOrEmpty(): CValuesRef<ByteVar> =
    if (isEmpty()) almostEmptyArray.addressOf(0) else refTo(0)

@OptIn(ExperimentalUnsignedTypes::class)
internal fun UByteArray.refToOrEmpty(): CValuesRef<UByteVar> =
    if (isEmpty()) almostEmptyUArray.addressOf(0) else refTo(0)