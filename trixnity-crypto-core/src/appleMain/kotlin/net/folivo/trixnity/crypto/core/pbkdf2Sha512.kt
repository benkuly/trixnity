package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.convert
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA512


@OptIn(ExperimentalUnsignedTypes::class)
actual suspend fun generatePbkdf2Sha512(
    password: String,
    salt: ByteArray,
    iterationCount: Int,
    keyBitLength: Int
): ByteArray {
    val output = ByteArray((keyBitLength / 8).takeIf { keyBitLength % 8 == 0 } ?: ((keyBitLength / 8) + 1))
    CCKeyDerivationPBKDF(
        // FIXME checkError
        algorithm = kCCPBKDF2,
        password = password,
        passwordLen = password.length.convert(),
        salt = salt.asUByteArray().refToOrEmpty(),
        saltLen = salt.size.convert(),
        prf = kCCPRFHmacAlgSHA512,
        rounds = iterationCount.convert(),
        derivedKey = output.asUByteArray().refToOrEmpty(),
        derivedKeyLen = output.size.convert(),
    )
    return output
}