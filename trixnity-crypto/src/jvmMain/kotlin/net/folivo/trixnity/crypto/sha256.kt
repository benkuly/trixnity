package net.folivo.trixnity.crypto

import kotlinx.coroutines.flow.*
import net.folivo.trixnity.core.ByteArrayFlow
import net.folivo.trixnity.olm.encodeUnpaddedBase64
import java.security.MessageDigest

actual fun ByteArrayFlow.sha256(): Sha256ByteFlow {
    val hash = MutableStateFlow<String?>(null)
    return Sha256ByteFlow(
        flow {
            val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
            emitAll(
                onEach { input ->
                    digest.update(input)
                }.onCompletion {
                    hash.value = digest.digest().encodeUnpaddedBase64()
                }
            )
        },
        hash
    )
}