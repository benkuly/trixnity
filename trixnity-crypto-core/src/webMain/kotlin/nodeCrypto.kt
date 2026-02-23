@file:JsModule("crypto")
@file:JsNonModule

import js.typedarrays.Int8Array
import js.typedarrays.Uint8Array


external fun randomFillSync(buffer: Int8Array<*>): Uint8Array<*>

external fun pbkdf2(
    password: String,
    salt: Uint8Array<*>,
    iterations: Number,
    keylen: Number,
    digest: String,
    callback: (err: Error?, derivedKey: Uint8Array<*>) -> Unit
)

external fun createCipheriv(
    algorithm: String,
    key: Uint8Array<*>,
    iv: Uint8Array<*>
): Cipher

external fun createDecipheriv(
    algorithm: String,
    key: Uint8Array<*>,
    iv: Uint8Array<*>,
): Decipher

external fun createHmac(
    algorithm: String,
    key: Uint8Array<*>,
): HMAC

external fun createHash(
    algorithm: String
): Hash

external interface Cipher {
    fun update(data: Uint8Array<*>): js.buffer.ArrayBuffer
    fun final(): js.buffer.ArrayBuffer
}

external interface Decipher {
    fun update(data: Uint8Array<*>): js.buffer.ArrayBuffer
    fun final(): js.buffer.ArrayBuffer
}

external interface HMAC {
    fun update(data: Uint8Array<*>)
    fun digest(): js.buffer.ArrayBuffer
}

external interface Hash {
    fun update(data: Uint8Array<*>)
    fun digest(): js.buffer.ArrayBuffer
}