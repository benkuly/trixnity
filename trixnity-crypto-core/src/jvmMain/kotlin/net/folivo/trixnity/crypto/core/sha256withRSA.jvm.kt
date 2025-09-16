package net.folivo.trixnity.crypto.core

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import kotlin.io.encoding.Base64

actual suspend fun signSha256WithRSA(key: String, data: ByteArray): ByteArray {
    val keySpec = PKCS8EncodedKeySpec(key.encodeToByteArray())
    val signer = Signature.getInstance("SHA256withRSA")
    signer.initSign(KeyFactory.getInstance("RSA").generatePrivate(keySpec))
    signer.update(data)
    return signer.sign()
}

actual suspend fun verifySha256WithRSA(key: String, payload: ByteArray, signature: ByteArray): Boolean {
    fun String.toX509PublicKey(): ByteArray = Base64.decode(
        replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
    )

    val keySpec = X509EncodedKeySpec(key.toX509PublicKey())
    val verifier = Signature.getInstance("SHA256withRSA")
    verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(keySpec))
    verifier.update(payload)
    return verifier.verify(signature)
}
