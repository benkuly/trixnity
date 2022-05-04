@file:JsModule("crypto")
@file:JsNonModule

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

external interface Cipher {
    fun update(data: Int8Array): Int8Array
    fun final(): Int8Array
}

external interface Decipher {
    fun update(data: Int8Array): Int8Array
    fun final(): Int8Array
}
