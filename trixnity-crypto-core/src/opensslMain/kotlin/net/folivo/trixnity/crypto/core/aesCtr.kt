package net.folivo.trixnity.crypto.core

import checkError
import kotlinx.cinterop.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.utils.ByteArrayFlow
import org.openssl.*

@OptIn(ExperimentalUnsignedTypes::class)
actual fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {
    val context = EVP_CIPHER_CTX_new()
    val cipher = EVP_aes_256_ctr()
    try {
        key.asUByteArray().usePinned { pinnedKey ->
            initialisationVector.asUByteArray().usePinned { pinnedInitialisationVector ->
                checkError(
                    EVP_EncryptInit_ex2(
                        ctx = context,
                        cipher = cipher,
                        key = pinnedKey.addressOf(0),
                        iv = pinnedInitialisationVector.addressOf(0),
                        params = null
                    )
                )
            }
        }
        val blockSize = checkError(EVP_CIPHER_CTX_get_block_size(context))
        filter { it.isNotEmpty() }.collect { input ->
            input.asUByteArray().usePinned { pinnedInput ->
                memScoped {
                    val output = ByteArray(input.size + blockSize)
                    val outputLength = alloc<IntVar>()
                    output.asUByteArray().usePinned { pinnedOutput ->
                        checkError(
                            EVP_EncryptUpdate(
                                ctx = context,
                                out = pinnedOutput.addressOf(0),
                                outl = outputLength.ptr,
                                `in` = pinnedInput.addressOf(0),
                                inl = input.size
                            )
                        )
                    }
                    emit(output.wrapSizeTo(outputLength.value))
                }
            }
        }
        memScoped {
            val output = ByteArray(blockSize)
            val outputLength = alloc<IntVar>()
            output.asUByteArray().usePinned { pinnedOutput ->
                checkError(
                    EVP_EncryptFinal_ex(
                        ctx = context,
                        out = pinnedOutput.addressOf(0),
                        outl = outputLength.ptr
                    )
                )
            }
            emit(output.wrapSizeTo(outputLength.value))
        }
    } finally {
        EVP_CIPHER_CTX_free(context)
        EVP_CIPHER_free(cipher)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
actual fun ByteArrayFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {
    val context = EVP_CIPHER_CTX_new()
    val cipher = EVP_aes_256_ctr()
    try {
        check(key.isNotEmpty()) { "key must not be empty" }
        check(initialisationVector.isNotEmpty()) { "key must not be empty" }
        key.asUByteArray().usePinned { pinnedKey ->
            initialisationVector.asUByteArray().usePinned { pinnedInitialisationVector ->
                checkError(
                    EVP_DecryptInit_ex2(
                        ctx = context,
                        cipher = cipher,
                        key = pinnedKey.addressOf(0),
                        iv = pinnedInitialisationVector.addressOf(0),
                        params = null
                    )
                )
            }
        }
        val blockSize = checkError(EVP_CIPHER_CTX_get_block_size(context))
        filter { it.isNotEmpty() }.collect { input ->
            input.asUByteArray().usePinned { pinnedInput ->
                memScoped {
                    val output = ByteArray(input.size + blockSize)
                    val outputLength = alloc<IntVar>()
                    output.asUByteArray().usePinned { pinnedOutput ->
                        checkError(
                            EVP_DecryptUpdate(
                                ctx = context,
                                out = pinnedOutput.addressOf(0),
                                outl = outputLength.ptr,
                                `in` = pinnedInput.addressOf(0),
                                inl = input.size
                            )
                        )
                    }
                    emit(output.wrapSizeTo(outputLength.value))
                }
            }
        }
        memScoped {
            val output = ByteArray(blockSize)
            val outputLength = alloc<IntVar>()
            output.asUByteArray().usePinned { pinnedOutput ->
                checkError(
                    EVP_DecryptFinal_ex(
                        ctx = context,
                        outm = pinnedOutput.addressOf(0),
                        outl = outputLength.ptr
                    )
                )
            }
            emit(output.wrapSizeTo(outputLength.value))
        }
    } catch (exception: Exception) {
        throw AesDecryptionException(exception)
    } finally {
        EVP_CIPHER_CTX_free(context)
        EVP_CIPHER_free(cipher)
    }
}