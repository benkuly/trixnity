external fun PBKDF2(password: String, salt: WordArray, cfg: KDFOption): WordArray

external var SHA512: HasherStatic

external interface HasherStatic

@Suppress("NESTED_CLASS_IN_EXTERNAL_INTERFACE")
external interface WordArray {
    var words: Array<Number>

    companion object {
        fun create(words: Array<Number> = definedExternally, sigBytes: Number = definedExternally): WordArray
    }
}

external interface KDFOption {
    var keySize: Number?
        get() = definedExternally
        set(value) = definedExternally
    var hasher: HasherStatic?
        get() = definedExternally
        set(value) = definedExternally
    var iterations: Number?
        get() = definedExternally
        set(value) = definedExternally
}