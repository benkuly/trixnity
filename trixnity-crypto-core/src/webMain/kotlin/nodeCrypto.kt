@file:JsModule("crypto")

import js.errors.JsError
import js.typedarrays.Uint8Array
import kotlin.js.JsModule
import kotlin.js.JsNumber


external fun randomFillSync(buffer: Uint8Array<js.buffer.ArrayBuffer>): Uint8Array<*>

external fun pbkdf2(
    password: String,
    salt: Uint8Array<*>,
    iterations: JsNumber,
    keylen: JsNumber,
    digest: String,
    callback: (err: JsError?, derivedKey: Uint8Array<*>) -> Unit
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