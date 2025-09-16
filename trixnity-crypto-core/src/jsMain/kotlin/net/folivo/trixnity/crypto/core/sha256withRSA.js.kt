package net.folivo.trixnity.crypto.core

import js.typedarrays.Uint8Array
import js.typedarrays.asInt8Array
import js.typedarrays.toByteArray
import js.typedarrays.toInt8Array
import js.typedarrays.toUint8Array
import web.crypto.Algorithm
import web.crypto.CryptoKey
import web.crypto.KeyFormat
import web.crypto.KeyUsage
import web.crypto.RsaHashedImportParams
import web.crypto.SubtleCrypto
import web.crypto.crypto
import web.crypto.importKey
import web.crypto.pkcs8
import web.crypto.sign
import web.crypto.spki
import web.crypto.verify

private suspend fun importPrivateKey(
    subtleCrypto: SubtleCrypto,
    params: RsaHashedImportParams,
    pkcs8: ByteArray
): CryptoKey = subtleCrypto.importKey(
    format = KeyFormat.pkcs8,
    keyData = pkcs8.toInt8Array(),
    algorithm = params,
    extractable = true,
    keyUsages = arrayOf(KeyUsage.sign)
)

private suspend fun importPublicKey(
    subtleCrypto: SubtleCrypto,
    params: RsaHashedImportParams,
    spki: ByteArray
): CryptoKey = subtleCrypto.importKey(
    format = KeyFormat.spki,
    keyData = spki.toInt8Array(),
    algorithm = params,
    extractable = true,
    keyUsages = arrayOf(KeyUsage.verify)
)

actual suspend fun signSha256WithRSA(key: ByteArray, data: ByteArray): ByteArray {
    val subtleCrypto = crypto.subtle
    val params = RsaHashedImportParams(name = "RSASSA-PKCS1-v1_5", hash = "SHA-256")
    val privateKey = importPrivateKey(subtleCrypto, params, key)
    return Uint8Array(subtleCrypto.sign(algorithm = params, key = privateKey, data = data.asInt8Array())).toByteArray()
}

actual suspend fun verifySha256WithRSA(key: ByteArray, payload: ByteArray, signature: ByteArray): Boolean {
    val subtleCrypto = crypto.subtle
    val params = RsaHashedImportParams(name = "RSASSA-PKCS1-v1_5", hash = "SHA-256")
    val publicKey = importPublicKey(subtleCrypto, params, key)
    return subtleCrypto.verify(
        algorithm = params,
        key = publicKey,
        signature = signature.toUint8Array(),
        data = payload.toUint8Array()
    )
}
