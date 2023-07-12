package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.*
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.utils.ByteArrayFlow
import platform.CoreCrypto.*
import platform.posix.*

actual fun ByteArrayFlow.encryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = withCCCryptor(kCCEncrypt, key, initialisationVector)

actual fun ByteArrayFlow.decryptAes256Ctr(
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow =
    try {
        check(key.isNotEmpty()) { "key must not be empty" }
        check(initialisationVector.isNotEmpty()) { "initialisationVector must not be empty" }
        withCCCryptor(kCCDecrypt, key, initialisationVector)
    } catch (error: Throwable) {
        throw AesDecryptionException(error)
    }

@OptIn(ExperimentalUnsignedTypes::class)
fun ByteArrayFlow.withCCCryptor(
    operation: CCOperation,
    key: ByteArray,
    initialisationVector: ByteArray
): ByteArrayFlow = flow {
    memScoped {
        val context = alloc<CCCryptorRefVar>()
        try {
            key.asUByteArray().usePinned { pinnedKey ->
                initialisationVector.asUByteArray().usePinned { pinnedInitialisationVector ->
                    checkError(
                        CCCryptorCreateWithMode(
                            op = operation,
                            mode = kCCModeCTR,
                            alg = kCCAlgorithmAES,
                            options = 0.convert(),
                            key = pinnedKey.addressOf(0),
                            keyLength = key.size.convert(),
                            iv = pinnedInitialisationVector.addressOf(0),
                            tweak = null,
                            tweakLength = 0.convert(),
                            numRounds = 0,
                            padding = 0u,
                            cryptorRef = context.ptr,
                        )
                    )
                }
            }
            filterNotEmpty().collect { input ->
                input.asUByteArray().usePinned { pinnedInput ->
                    memScoped {
                        val predictedOutputLength: Int =
                            CCCryptorGetOutputLength(
                                cryptorRef = context.value,
                                inputLength = input.size.convert(),
                                final = false
                            ).convert()
                        val output = ByteArray(predictedOutputLength)
                        val outputLength = alloc<size_tVar>()
                        output.asUByteArray().usePinned { pinnedOutput ->
                            checkError(
                                CCCryptorUpdate(
                                    cryptorRef = context.value,
                                    dataIn = pinnedInput.addressOf(0),
                                    dataInLength = input.size.convert(),
                                    dataOut = pinnedOutput.addressOf(0),
                                    dataOutAvailable = output.size.convert(),
                                    dataOutMoved = outputLength.ptr,
                                )
                            )
                        }
                        emit(output.wrapSizeTo(outputLength.value.convert()))
                    }
                }
            }
            memScoped {
                val predictedOutputLength: Int =
                    CCCryptorGetOutputLength(
                        cryptorRef = context.value,
                        inputLength = 0.convert(),
                        final = true
                    ).convert()
                val output = ByteArray(predictedOutputLength.coerceAtLeast(1))
                val outputLength = alloc<size_tVar>()
                output.asUByteArray().usePinned { pinnedOutput ->
                    checkError(
                        CCCryptorFinal(
                            cryptorRef = context.value,
                            dataOut = pinnedOutput.addressOf(0),
                            dataOutAvailable = output.size.convert(),
                            dataOutMoved = outputLength.ptr,
                        )
                    )
                }
                emit(output.wrapSizeTo(outputLength.value.convert()))
            }
        } finally {
            CCCryptorRelease(context.value)
        }
    }
}.filterNotEmpty()