package net.folivo.trixnity.client.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal actual suspend fun generatePbkdf2Sha512(
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