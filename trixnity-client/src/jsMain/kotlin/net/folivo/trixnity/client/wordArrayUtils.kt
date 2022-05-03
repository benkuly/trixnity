package net.folivo.trixnity.client

import global.CryptoJS.lib.WordArray

fun ByteArray.toWordArray(): WordArray = WordArray.create(toList().chunked(4).map {
    it.reversed()
        .mapIndexed { index, byte -> byte.toUInt() shl index * 8 }
        .reduce { acc, next -> acc or next }
        .toInt()
}.toTypedArray())

fun WordArray.toByteArray(): ByteArray = words.toList().flatMap {
    val number = it.toInt()
    (3 downTo 0).map { index -> (number shr index * 8).toByte() }
}.toByteArray()