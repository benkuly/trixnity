package de.connect2x.trixnity.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.Base64.PaddingOption.ABSENT_OPTIONAL

private val base64 = Base64.withPadding(ABSENT_OPTIONAL)

fun ByteArray.encodeUnpaddedBase64(): String = base64.encode(this)

fun String.decodeUnpaddedBase64Bytes(): ByteArray = base64.decode(this)