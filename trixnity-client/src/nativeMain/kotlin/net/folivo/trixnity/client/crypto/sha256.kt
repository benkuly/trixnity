package net.folivo.trixnity.client.crypto

import com.soywiz.krypto.SHA256

actual suspend fun sha256(input: ByteArray): String = SHA256.digest(input).hex