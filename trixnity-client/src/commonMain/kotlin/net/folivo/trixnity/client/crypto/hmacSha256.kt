package net.folivo.trixnity.client.crypto

expect suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray