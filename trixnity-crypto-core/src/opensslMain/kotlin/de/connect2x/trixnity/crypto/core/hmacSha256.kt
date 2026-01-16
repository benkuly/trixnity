package de.connect2x.trixnity.crypto.core

import checkError
import kotlinx.cinterop.*
import org.openssl.*


@OptIn(ExperimentalUnsignedTypes::class)
actual suspend fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = memScoped {
    val mac = checkError(EVP_MAC_fetch(null, "HMAC", null))
    val context = checkError(EVP_MAC_CTX_new(mac))
    try {
        key.asUByteArray().usePinned { pinnedKey ->
            checkError(
                EVP_MAC_init(
                    ctx = context,
                    key = pinnedKey.addressOf(0),
                    keylen = key.size.convert(),
                    params = OSSL_PARAM_array(
                        OSSL_PARAM_construct_utf8_string("digest".cstr.ptr, "SHA256".cstr.ptr, 0U)
                    )
                )
            )
        }
        data.asUByteArray().usePinned { pinnedData ->
            checkError(EVP_MAC_update(context, pinnedData.addressOf(0), data.size.convert()))
        }
        val signature = ByteArray(checkError(EVP_MAC_CTX_get_mac_size(context)).convert())
        signature.asUByteArray().usePinned { pinnedSignature ->
            checkError(EVP_MAC_final(context, pinnedSignature.addressOf(0), null, signature.size.convert()))
        }
        signature
    } finally {
        EVP_MAC_CTX_free(context)
        EVP_MAC_free(mac)
    }
}