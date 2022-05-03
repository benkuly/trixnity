@file:JsQualifier("lib")
@file:JsModule("crypto-js")
@file:JsNonModule

package global.CryptoJS.lib

@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
external interface WordArray {
    var words: Array<Number>

    companion object {
        fun create(words: Array<Number> = definedExternally, sigBytes: Number = definedExternally): WordArray
    }
}

external interface Hasher