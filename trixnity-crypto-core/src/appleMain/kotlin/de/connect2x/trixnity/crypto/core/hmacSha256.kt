package de.connect2x.trixnity.crypto.core

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256


@OptIn(ExperimentalUnsignedTypes::class)
actual suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val output = ByteArray(CC_SHA256_DIGEST_LENGTH)
    key.asUByteArray().usePinned { pinnedKey ->
        data.asUByteArray().usePinned { pinnedData ->
            output.asUByteArray().usePinned { pinnedOutput ->
                CCHmac(
                    algorithm = kCCHmacAlgSHA256,
                    key = pinnedKey.addressOf(0),
                    keyLength = key.size.convert(),
                    data = pinnedData.addressOf(0),
                    dataLength = data.size.convert(),
                    macOut = pinnedOutput.addressOf(0),
                )
            }
        }
    }
    return output
}