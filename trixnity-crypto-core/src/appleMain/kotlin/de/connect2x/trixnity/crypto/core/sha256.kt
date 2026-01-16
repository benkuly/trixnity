package de.connect2x.trixnity.crypto.core

import kotlinx.cinterop.*
import platform.CoreCrypto.*

private class AppleSha256 : Hasher {

    private val context = nativeHeap.alloc<CC_SHA256_CTX>()
    private var closed = false
    private var ready = false

    override fun update(input: ByteArray) {
        check(!closed) { "SHA-256 is closed" }
        if (input.isEmpty()) return
        ensureReady()

        input.usePinned {
            CC_SHA256_Update(context.ptr, it.addressOf(0), input.size.convert())
        }
    }

    override fun digest(): ByteArray {
        check(!closed) { "SHA-256 is closed" }
        ensureReady()

        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        digest.asUByteArray().usePinned {
            CC_SHA256_Final(it.addressOf(0), context.ptr)
        }
        ready = false

        return digest
    }

    override fun close() {
        if (closed) return
        closed = true

        nativeHeap.free(context)
    }

    private fun ensureReady() {
        if (ready) return

        CC_SHA256_Init(context.ptr)
        ready = true
    }

}

actual fun Sha256(): Hasher = AppleSha256()