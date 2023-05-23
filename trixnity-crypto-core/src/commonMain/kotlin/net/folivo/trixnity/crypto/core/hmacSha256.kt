package net.folivo.trixnity.crypto.core

expect suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray