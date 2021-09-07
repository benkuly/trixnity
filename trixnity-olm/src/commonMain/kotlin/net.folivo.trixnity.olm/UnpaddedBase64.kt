package net.folivo.trixnity.olm

import io.ktor.util.*


@OptIn(InternalAPI::class)
fun ByteArray.encodeUnpaddedBase64(): String = this.encodeBase64().substringBefore("=")

@OptIn(InternalAPI::class)
fun String.decodeUnpaddedBase64Bytes(): ByteArray = this.decodeBase64Bytes()