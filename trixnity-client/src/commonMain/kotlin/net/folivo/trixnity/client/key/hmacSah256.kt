package net.folivo.trixnity.client.key

expect suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray