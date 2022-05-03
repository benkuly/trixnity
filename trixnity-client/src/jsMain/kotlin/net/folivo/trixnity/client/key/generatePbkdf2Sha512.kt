package net.folivo.trixnity.client.key

import global.CryptoJS.algo.PBKDF2
import global.CryptoJS.algo.SHA512
import net.folivo.trixnity.client.toByteArray
import net.folivo.trixnity.client.toWordArray
import kotlin.js.json

internal actual suspend fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray =
    PBKDF2.create(
        cfg = json(
            "keySize" to keyBitLength / 32,
            "iterations" to iterationCount,
            "hasher" to SHA512
        )
    ).compute(
        password = password,
        salt = salt.toWordArray()
    ).toByteArray()