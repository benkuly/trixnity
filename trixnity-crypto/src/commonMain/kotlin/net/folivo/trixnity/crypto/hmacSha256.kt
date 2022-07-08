package net.folivo.trixnity.crypto

expect suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray