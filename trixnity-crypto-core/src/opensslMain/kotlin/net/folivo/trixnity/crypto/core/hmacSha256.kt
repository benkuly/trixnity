package net.folivo.trixnity.crypto.core

import checkError
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import org.openssl.*


@OptIn(ExperimentalUnsignedTypes::class)
actual suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = memScoped {
    val mac = checkError(EVP_MAC_fetch(null, "HMAC", null))
    val context = checkError(EVP_MAC_CTX_new(mac))
    try {
        checkError(
            EVP_MAC_init(
                ctx = context,
                key = key.asUByteArray().refToOrEmpty(),
                keylen = key.size.convert(),
                params = OSSL_PARAM_array(
                    OSSL_PARAM_construct_utf8_string("digest".cstr.ptr, "SHA256".cstr.ptr, 0U)
                )
            )
        )
        checkError(EVP_MAC_update(context, data.asUByteArray().refToOrEmpty(), data.size.convert()))
        val signature = ByteArray(checkError(EVP_MAC_CTX_get_mac_size(context)).convert())
        checkError(EVP_MAC_final(context, signature.asUByteArray().refToOrEmpty(), null, signature.size.convert()))
        signature
    } finally {
        EVP_MAC_CTX_free(context)
        EVP_MAC_free(mac)
    }
}