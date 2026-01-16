package de.connect2x.trixnity.crypto.core

import java.security.MessageDigest

private class JvmSha256 : Hasher {

    private var closed = false

    private val digest = MessageDigest.getInstance("SHA-256")

    override fun update(input: ByteArray) {
        check(!closed) { "SHA-256 is closed" }
        if (input.isEmpty()) return

        digest.update(input)
    }

    override fun digest(): ByteArray {
        check(!closed) { "SHA-256 is closed" }

        return digest.digest()
    }

    override fun close() {
        closed = true
    }
}

actual fun Sha256(): Hasher = JvmSha256()