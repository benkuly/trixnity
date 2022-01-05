package net.folivo.trixnity.client.key

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal actual fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray {
    val skf: SecretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
    return skf.generateSecret(
        PBEKeySpec(password.toCharArray(), salt, iterationCount, keyBitLength)
    ).encoded
}