package de.connect2x.trixnity.crypto.core

import checkError
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.openssl.EVP_MD_free
import org.openssl.EVP_sha512
import org.openssl.PKCS5_PBKDF2_HMAC


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
            val digest = EVP_sha512()
            try {
                checkError(
                    PKCS5_PBKDF2_HMAC(
                        pass = password,
                        passlen = password.length,
                        salt = pinnedSalt.addressOf(0),
                        saltlen = salt.size,
                        iter = iterationCount,
                        digest = digest,
                        keylen = output.size,
                        out = pinnedOutput.addressOf(0),
                    )
                )
            } finally {
                EVP_MD_free(digest)
            }
        }
    }
    return output
}