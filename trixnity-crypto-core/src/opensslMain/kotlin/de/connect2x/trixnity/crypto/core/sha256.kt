package de.connect2x.trixnity.crypto.core

import checkError
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import org.openssl.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
private class OpenSslSha256 : Hasher {

    private var closed = false
    private var ready = false

    private val md = checkError(EVP_MD_fetch(null, "SHA256", null))
    private val mdSize = EVP_MD_get_size(md)
    private val context = checkError(EVP_MD_CTX_new())

    override fun update(input: ByteArray) {
        check(!closed) { "SHA-256 is closed" }
        if (input.isEmpty()) return
        ensureReady()

        input.usePinned {
            checkError(EVP_DigestUpdate(context, it.addressOf(0), input.size.convert()))
        }
    }

    override fun digest(): ByteArray {
        check(!closed) { "SHA-256 is closed" }
        ensureReady()

        val digest = ByteArray(mdSize)
        digest.asUByteArray().usePinned {
            checkError(EVP_DigestFinal_ex(context, it.addressOf(0), null))
        }
        ready = false

        return digest
    }

    private fun ensureReady() {
        if (ready) return

        checkError(EVP_DigestInit_ex2(context, md, null))
        ready = true
    }

    override fun close() {
        if (closed) return
        closed = true

        EVP_MD_CTX_free(context)
        EVP_MD_free(md)
    }
}

actual fun Sha256(): Hasher = OpenSslSha256()