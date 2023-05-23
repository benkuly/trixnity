package net.folivo.trixnity.utils

import io.ktor.util.*


fun ByteArray.encodeUnpaddedBase64(): String = this.encodeBase64().substringBefore("=")

fun String.decodeUnpaddedBase64Bytes(): ByteArray = this.decodeBase64Bytes()