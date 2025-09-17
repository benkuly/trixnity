package net.folivo.trixnity.crypto.core

import js.typedarrays.Uint8Array
import js.typedarrays.asInt8Array
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
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
import kotlin.io.encoding.Base64

private suspend fun importKey(
    subtleCrypto: SubtleCrypto,
    params: RsaHashedImportParams,
    data: String,
    format: KeyFormat,
    keyUsages: Array<KeyUsage>
): CryptoKey {
    fun String.toX509PublicKey(): ByteArray = Base64.decode(
        replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
    )

    return subtleCrypto.importKey(
        format = format,
        keyData = data.toX509PublicKey().toUint8Array(),
        algorithm = params,
        extractable = true,
        keyUsages = keyUsages
    )
}

actual suspend fun signSha256WithRSA(key: String, data: ByteArray): ByteArray {
    val subtleCrypto = crypto.subtle
    val params = RsaHashedImportParams(name = "RSASSA-PKCS1-v1_5", hash = "SHA-256")
    val privateKey = importKey(subtleCrypto, params, key, KeyFormat.pkcs8, arrayOf(KeyUsage.sign))
    return Uint8Array(subtleCrypto.sign(algorithm = params, key = privateKey, data = data.asInt8Array())).toByteArray()
}

actual suspend fun verifySha256WithRSA(key: String, payload: ByteArray, signature: ByteArray): Boolean {
    val subtleCrypto = crypto.subtle
    val params = RsaHashedImportParams(name = "RSASSA-PKCS1-v1_5", hash = "SHA-256")
    val publicKey = importKey(subtleCrypto, params, key, KeyFormat.spki, arrayOf(KeyUsage.verify))
    return subtleCrypto.verify(
        algorithm = params,
        key = publicKey,
        signature = signature.toUint8Array(),
        data = payload.toUint8Array()
    )
}
