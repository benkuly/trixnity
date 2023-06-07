package net.folivo.trixnity.crypto.core

internal fun ByteArray.wrapSizeTo(expectedSize: Int): ByteArray = when (size) {
    expectedSize -> this
    else -> copyOf(expectedSize)
}