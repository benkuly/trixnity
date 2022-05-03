@file:JsQualifier("algo")
@file:JsModule("crypto-js")
@file:JsNonModule

package global.CryptoJS.algo

import global.CryptoJS.lib.Hasher
import global.CryptoJS.lib.WordArray
import kotlin.js.Json

external var SHA512: Hasher

@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
external interface PBKDF2 {
    fun compute(password: String, salt: WordArray): WordArray

    companion object {
        fun create(cfg: Json): PBKDF2
    }
}