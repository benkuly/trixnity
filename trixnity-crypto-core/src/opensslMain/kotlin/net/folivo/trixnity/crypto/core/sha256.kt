package net.folivo.trixnity.crypto.core

import checkError
import kotlinx.cinterop.convert
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.encodeUnpaddedBase64
import org.openssl.*

@OptIn(ExperimentalUnsignedTypes::class)
actual fun ByteArrayFlow.sha256(): Sha256ByteFlow {
    val hash = MutableStateFlow<String?>(null)
    return Sha256ByteFlow(
        flow {
            val md = EVP_MD_fetch(null, "SHA256", null)
            val context = checkError(EVP_MD_CTX_new())
            try {
                val digestSize = EVP_MD_get_size(md)
                val digest = ByteArray(digestSize)

                checkError(EVP_DigestInit(context, md))
                emitAll(
                    filter { it.isNotEmpty() }.onEach { input ->
                        checkError(EVP_DigestUpdate(context, input.refToOrEmpty(), input.size.convert()))
                    }.onCompletion {
                        checkError(EVP_DigestFinal(context, digest.asUByteArray().refToOrEmpty(), null))
                        hash.value = digest.encodeUnpaddedBase64()
                    }
                )
            } finally {
                EVP_MD_CTX_free(context)
                EVP_MD_free(md)
            }
        },
        hash
    )
}