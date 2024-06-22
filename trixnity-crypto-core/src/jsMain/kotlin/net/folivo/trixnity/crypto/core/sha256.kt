package net.folivo.trixnity.crypto.core

import createHash
import io.ktor.util.*
import js.typedarrays.Uint8Array
import js.typedarrays.toUint8Array
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.encodeUnpaddedBase64
import net.folivo.trixnity.utils.toByteArray
import web.crypto.crypto

actual fun ByteArrayFlow.sha256(): Sha256ByteFlow {
    return if (PlatformUtils.IS_BROWSER) {
        val crypto = crypto.subtle
        val hash = MutableStateFlow<String?>(null)
        Sha256ByteFlow(
            flow {// TODO should be streaming!
                val content = toByteArray()
                hash.value = Uint8Array(crypto.digest("SHA-256", content.toUint8Array()))
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
                        digest.update(input.toUint8Array())
                    }.onCompletion {
                        hash.value = Uint8Array(digest.digest()).toByteArray().encodeUnpaddedBase64()
                    }
                )
            },
            hash,
        )
    }
}