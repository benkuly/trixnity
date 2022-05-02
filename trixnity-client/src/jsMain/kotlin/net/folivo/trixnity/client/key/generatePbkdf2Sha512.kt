package net.folivo.trixnity.client.key

import HasherStatic
import KDFOption
import PBKDF2
import SHA512
import WordArray

internal actual suspend fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray {
    val result: WordArray = PBKDF2(
        password = password,
        salt = WordArray.create(salt.toList().chunked(4).map {
            (it[3].toInt() shl 24) or
                    (it[2].toInt() and 0xff shl 16) or
                    (it[1].toInt() and 0xff shl 8) or
                    (it[0].toInt() and 0xff)
        }.toTypedArray()),
        cfg = object : KDFOption {
            override var iterations: Number? = iterationCount
            override var hasher: HasherStatic? = SHA512
        })
    return result.words.toList().chunked(4).flatMap {
        listOf(
            (it[0].toInt() shr 0).toByte(),
            (it[1].toInt() shr 8).toByte(),
            (it[2].toInt() shr 16).toByte(),
            (it[3].toInt() shr 24).toByte(),
        )
    }.toByteArray()
}