package net.folivo.trixnity.crypto.core

import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import org.openssl.BIO_free
import org.openssl.BIO_new_mem_buf
import org.openssl.EVP_DigestSignFinal
import org.openssl.EVP_DigestSignInit
import org.openssl.EVP_DigestSignUpdate
import org.openssl.EVP_DigestVerifyFinal
import org.openssl.EVP_DigestVerifyInit
import org.openssl.EVP_DigestVerifyUpdate
import org.openssl.EVP_MD_CTX_free
import org.openssl.EVP_MD_CTX_new
import org.openssl.EVP_PKEY_free
import org.openssl.EVP_sha256
import org.openssl.PEM_read_bio_PUBKEY
import org.openssl.PEM_read_bio_PrivateKey
import platform.posix.size_tVar

actual suspend fun signSha256WithRSA(key: String, data: ByteArray): ByteArray = memScoped {
    val rawKey = key.encodeToByteArray()
    val bio = BIO_new_mem_buf(rawKey.refTo(0), rawKey.size) ?: error("Unable to create I/O object for key")
    val pkey = PEM_read_bio_PrivateKey(bio, null, null, null) ?: error("Unable to create private key")
    BIO_free(bio)

    // Initialize signer
    val digestContext = EVP_MD_CTX_new() ?: error("Unable to initialize signer")
    if (EVP_DigestSignInit(digestContext, null, EVP_sha256(), null, pkey) != 1) {
        EVP_MD_CTX_free(digestContext)
        EVP_PKEY_free(pkey)
        error("Unable to initializer signer (SHA256 with RSA)")
    }

    // Insert payload into signer
    if (EVP_DigestSignUpdate(digestContext, data.refTo(0), data.size.convert()) != 1) {
        EVP_MD_CTX_free(digestContext)
        EVP_PKEY_free(pkey)
        error("Unable to sign data")
    }

    // Get and return signature
    val signatureLengthVar = alloc<size_tVar>()
    EVP_DigestSignFinal(digestContext, null, signatureLengthVar.ptr)
    val signatureLength = signatureLengthVar.value.toInt()

    val signature = UByteArray(signatureLength)
    EVP_DigestSignFinal(digestContext, signature.refTo(0), signatureLengthVar.ptr)

    EVP_MD_CTX_free(digestContext)
    EVP_PKEY_free(pkey)
    return signature.toByteArray()
}

actual suspend fun verifySha256WithRSA(key: String, payload: ByteArray, signature: ByteArray): Boolean {
    val rawKey = key.encodeToByteArray()
    val bio = BIO_new_mem_buf(rawKey.refTo(0), rawKey.size) ?: error("Unable to create I/O object for key")
    val pkey = PEM_read_bio_PUBKEY(bio, null, null, null) ?: error("Unable to create private key")
    BIO_free(bio)

    // Initialize verifier
    val digestContext = EVP_MD_CTX_new() ?: error("Unable to initialize signer")
    if (EVP_DigestVerifyInit(digestContext, null, EVP_sha256(), null, pkey) != 1) {
        EVP_MD_CTX_free(digestContext)
        EVP_PKEY_free(pkey)
        error("Unable to initialize signer")
    }

    // Insert payload into verifier
    if (EVP_DigestVerifyUpdate(digestContext, payload.refTo(0), payload.size.convert()) != 1) {
        EVP_MD_CTX_free(digestContext)
        EVP_PKEY_free(pkey)
        error("Unable to sign data")
    }

    // Verify signature and return
    val valid = EVP_DigestVerifyFinal(digestContext, signature.toUByteArray().refTo(0), signature.size.convert())
    EVP_MD_CTX_free(digestContext)
    EVP_PKEY_free(pkey)
    return valid == 1
}
