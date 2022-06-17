package net.folivo.trixnity.client.crypto

import java.security.MessageDigest

actual suspend fun sha256(input: ByteArray): String {
    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
    return digest.digest(input).joinToString("") { "%02x".format(it) }
}