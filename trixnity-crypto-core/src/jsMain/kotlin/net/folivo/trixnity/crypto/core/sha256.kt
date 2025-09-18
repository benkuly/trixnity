package net.folivo.trixnity.crypto.core

import Hash
import createHash
import io.ktor.util.*
import js.typedarrays.Uint8Array
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import okio.Buffer
import okio.HashingSink
import okio.blackholeSink
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private class BrowserSha256 : Hasher {

    private val sink: HashingSink = HashingSink.sha256(blackholeSink())
    private var closed = false

    override fun update(input: ByteArray) {
        check(!closed) { "SHA-256 is closed" }
        if (input.isEmpty()) return

        val buffer = Buffer().write(input)
        sink.write(buffer, buffer.size)
    }

    override fun digest(): ByteArray {
        check(!closed) { "SHA-256 is closed" }

        return sink.hash.toByteArray()
    }

    override fun close() {
        if (closed) return
        closed = true

        sink.close()
    }
}

@OptIn(ExperimentalAtomicApi::class)
private class NodeJsSha256 : Hasher {

    private var closed = false
    private var digest = makeDigest()

    override fun update(input: ByteArray) {
        check(!closed) { "SHA-256 is closed" }
        if (input.isEmpty()) return

        digest.update(input.toUint8Array())
    }

    override fun digest(): ByteArray {
        check(!closed) { "SHA-256 is closed" }

        return Uint8Array(rotate().digest()).toByteArray()
    }

    override fun close() {
        closed = true
    }

    private fun makeDigest() = createHash("sha256")

    private fun rotate(): Hash {
        val oldDigest = digest
        digest = makeDigest()

        return oldDigest
    }
}

actual fun Sha256(): Hasher = when {
    PlatformUtils.IS_BROWSER -> BrowserSha256()
    else -> NodeJsSha256()
}