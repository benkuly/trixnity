package net.folivo.trixnity.client.key

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val prf = Mac.getInstance("HmacSHA256")
    prf.init(SecretKeySpec(key, "HmacSHA256"))
    val output = ByteArray(32)
    prf.update(data)
    prf.doFinal(output, 0)
    return output
}

