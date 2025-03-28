package net.folivo.trixnity.crypto.core

import createHash
import io.ktor.util.*
import js.typedarrays.Uint8Array
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.utils.ByteArrayFlow
import net.folivo.trixnity.utils.encodeUnpaddedBase64
import okio.Buffer
import okio.HashingSink
import okio.blackholeSink

actual fun ByteArrayFlow.sha256(): Sha256ByteFlow {
    return if (PlatformUtils.IS_BROWSER) {
        val hash = MutableStateFlow<String?>(null)
        Sha256ByteFlow(
            flow {
                val sink: HashingSink = HashingSink.sha256(blackholeSink())
                emitAll(
                    onEach {
                        val buffer = Buffer().write(it)
                        sink.write(buffer, buffer.size)
                    }.onCompletion {
                        hash.value = sink.hash.toByteArray().encodeUnpaddedBase64()
                        sink.close()
                    }
                )
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