package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.encodeUnpaddedBase64
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update

@OptIn(ExperimentalUnsignedTypes::class)
actual fun ByteArrayFlow.sha256(): Sha256ByteFlow {
    val hash = MutableStateFlow<String?>(null)
    return Sha256ByteFlow(
        flow {
            memScoped {
                val context = alloc<CC_SHA256_CTX>()
                val digestSize = CC_SHA256_DIGEST_LENGTH
                val digest = ByteArray(digestSize)

                CC_SHA256_Init(context.ptr)
                emitAll(
                    filter { it.isNotEmpty() }.onEach { input ->
                        input.asUByteArray().usePinned { pinnedInput ->
                            CC_SHA256_Update(
                                context.ptr,
                                pinnedInput.addressOf(0),
                                input.size.convert()
                            )
                        }
                    }.onCompletion {
                        digest.asUByteArray().usePinned { pinnedDigest ->
                            CC_SHA256_Final(pinnedDigest.addressOf(0), context.ptr)
                        }
                        hash.value = digest.encodeUnpaddedBase64()
                    }
                )
            }
        },
        hash
    )
}