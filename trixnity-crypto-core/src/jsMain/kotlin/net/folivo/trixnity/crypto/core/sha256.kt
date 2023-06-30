package net.folivo.trixnity.crypto.core

import createHash
import crypto
import io.ktor.util.*
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.encodeUnpaddedBase64
import net.folivo.trixnity.utils.toByteArray

actual fun ByteArrayFlow.sha256(): Sha256ByteFlow {
    return if (PlatformUtils.IS_BROWSER) {
        val crypto = crypto.subtle
        val hash = MutableStateFlow<String?>(null)
        Sha256ByteFlow(
            flow {// TODO should be streaming!
                val content = toByteArray()
                hash.value = crypto.digest("SHA-256", content.toInt8Array().buffer).await()
                    .toByteArray()
                    .encodeUnpaddedBase64()
                emit(content)
            },
            hash,
        )
    } else {
        val hash = MutableStateFlow<String?>(null)
        Sha256ByteFlow(
            flow {
                val digest = createHash("sha256")
                emitAll(
                    onEach { input ->
                        digest.update(input.toInt8Array())
                    }.onCompletion {
                        hash.value = digest.digest().toByteArray().encodeUnpaddedBase64()
                    }
                )
            },
            hash,
        )
    }
}