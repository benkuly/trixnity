package net.folivo.trixnity.crypto.core

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

actual suspend fun signSha256WithRSA(key: ByteArray, data: ByteArray): ByteArray {
    val keySpec = PKCS8EncodedKeySpec(key)
    val signer = Signature.getInstance("SHA256withRSA")
    signer.initSign(KeyFactory.getInstance("RSA").generatePrivate(keySpec))
    signer.update(data)
    return signer.sign()
}

actual suspend fun verifySha256WithRSA(key: ByteArray, payload: ByteArray, signature: ByteArray): Boolean {
    val keySpec = X509EncodedKeySpec(key)
    val verifier = Signature.getInstance("SHA256withRSA")
    verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(keySpec))
    verifier.update(payload)
    return verifier.verify(signature)
}
