package net.folivo.trixnity.crypto

import com.soywiz.krypto.SHA256
import net.folivo.trixnity.olm.encodeUnpaddedBase64

actual suspend fun sha256(input: ByteArray): String = SHA256.digest(input).bytes.encodeUnpaddedBase64()