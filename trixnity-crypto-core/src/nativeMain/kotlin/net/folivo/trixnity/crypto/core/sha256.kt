package net.folivo.trixnity.crypto.core

import korlibs.crypto.SHA256
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.encodeUnpaddedBase64

actual fun ByteArrayFlow.sha256(): Sha256ByteFlow {
    val hash = MutableStateFlow<String?>(null)
    return Sha256ByteFlow(
        flow {
            val digest = SHA256()
            emitAll(
                onEach { input ->
                    digest.update(input)
                }.onCompletion {
                    hash.value = digest.digest().bytes.encodeUnpaddedBase64()
                }
            )
        },
        hash,
    )
}