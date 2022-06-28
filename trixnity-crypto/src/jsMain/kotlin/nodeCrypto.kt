@file:JsModule("crypto")
@file:JsNonModule

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array

external fun pbkdf2(
    password: String,
    salt: Int8Array,
    iterations: Number,
    keylen: Number,
    digest: String,
    callback: (err: Error?, derivedKey: Int8Array) -> Unit
)

external fun createCipheriv(
    algorithm: String,
    key: Int8Array,
    iv: Int8Array
): Cipher

external fun createDecipheriv(
    algorithm: String,
    key: Int8Array,
    iv: Int8Array
): Decipher

external fun createHmac(
    algorithm: String,
    key: Int8Array
): HMAC

external fun createHash(
    algorithm: String
): Hash

external interface Cipher {
    fun update(data: Int8Array): ArrayBuffer
    fun final(): ArrayBuffer
}

external interface Decipher {
    fun update(data: Int8Array): ArrayBuffer
    fun final(): ArrayBuffer
}

external interface HMAC {
    fun update(data: Int8Array)
    fun digest(): ArrayBuffer
}

external interface Hash {
    fun update(data: Int8Array)
    fun digest(): ArrayBuffer
}