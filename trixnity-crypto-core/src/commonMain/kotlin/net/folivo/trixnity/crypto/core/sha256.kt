package net.folivo.trixnity.crypto.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.encodeUnpaddedBase64

expect fun Sha256(): Hasher

class Sha256ByteFlow(
    content: ByteArrayFlow,
    val hash: StateFlow<String?>,
) : ByteArrayFlow by content

fun ByteArrayFlow.sha256(): Sha256ByteFlow {
    val hash = MutableStateFlow<String?>(null)

    val sha256 = Sha256()

    val content = this
        .filterNotEmpty()
        .onEach { sha256.update(it) }
        .onCompletion {
            hash.value = sha256.digest().encodeUnpaddedBase64()
            sha256.close()
        }

    return Sha256ByteFlow(content, hash)
}