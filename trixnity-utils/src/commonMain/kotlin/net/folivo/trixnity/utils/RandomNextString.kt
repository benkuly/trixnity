package net.folivo.trixnity.utils

import okio.ByteString.Companion.toByteString
import kotlin.math.ceil
import kotlin.random.Random

fun Random.nextString(length: Int): String =
    nextBytes(ceil(length * 3 / 4.0).toInt())
        .toByteString()
        .base64Url()
        .substringBefore('=')
        .substring(0, length)
