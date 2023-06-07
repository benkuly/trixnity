package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
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
    salt.asUByteArray().usePinned { pinnedSalt ->
        output.asUByteArray().usePinned { pinnedOutput ->
            val result = CCKeyDerivationPBKDF(
                algorithm = kCCPBKDF2,
                password = password,
                passwordLen = password.length.convert(),
                salt = pinnedSalt.addressOf(0),
                saltLen = salt.size.convert(),
                prf = kCCPRFHmacAlgSHA512,
                rounds = iterationCount.convert(),
                derivedKey = pinnedOutput.addressOf(0),
                derivedKeyLen = output.size.convert(),
            )
            if (result <= 0) error("unknown error with generatePbkdf2Sha512")
        }
    }
    return output
}