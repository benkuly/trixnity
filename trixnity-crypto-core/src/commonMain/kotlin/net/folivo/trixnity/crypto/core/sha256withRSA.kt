package net.folivo.trixnity.crypto.core

expect suspend fun signSha256WithRSA(key: String, data: ByteArray): ByteArray
expect suspend fun verifySha256WithRSA(key: String, payload: ByteArray, signature: ByteArray): Boolean
