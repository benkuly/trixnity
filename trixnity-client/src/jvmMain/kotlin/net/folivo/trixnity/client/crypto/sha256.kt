package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.olm.encodeUnpaddedBase64
import java.security.MessageDigest

actual suspend fun sha256(input: ByteArray): String {
    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
    return digest.digest(input).encodeUnpaddedBase64()
}