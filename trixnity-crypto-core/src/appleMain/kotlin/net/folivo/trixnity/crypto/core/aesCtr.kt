package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.*
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.utils.ByteArrayFlow
import platform.CoreCrypto.*

actual fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {
    memScoped {
        val cryptorRef = alloc<CCCryptorRefVar>()
        try {
            checkResult(
                CCCryptorCreate(
                    op = kCCDecrypt,
                    alg = kCCAlgorithmAES,
                    options = 0.convert(),
                    key = key.refTo(0),
                    keyLength = key.size.convert(),
                    iv = initialisationVector.refTo(0),
                    cryptorRef = cryptor,
                )
            )
        } finally {
            CCCryptorRelease(cryptor.value)
        }
    }
}

actual fun ByteArrayFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = TODO()

private fun checkResult(result: CCCryptorStatus) {
    val message = when (result) {
        kCCSuccess -> return
        kCCParamError -> "Illegal parameter value."
        kCCBufferTooSmall -> "Insufficent buffer provided for specified operation."
        kCCMemoryFailure -> "Memory allocation failure."
        kCCAlignmentError -> "Input size was not aligned properly."
        kCCDecodeError -> "Input data did not decode or decrypt properly."
        kCCUnimplemented -> "Function not implemented for the current algorithm."
        else -> "CCCrypt failed with code $result"
    }
    error(message)
}