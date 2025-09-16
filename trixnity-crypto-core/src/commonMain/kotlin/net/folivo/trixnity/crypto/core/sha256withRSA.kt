package net.folivo.trixnity.crypto.core

expect suspend fun signSha256WithRSA(key: ByteArray, data: ByteArray): ByteArray
expect suspend fun verifySha256WithRSA(key: ByteArray, payload: ByteArray, signature: ByteArray): Boolean
