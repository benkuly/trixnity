import org.khronos.webgl.ArrayBuffer
import kotlin.js.Json
import kotlin.js.Promise

external val crypto: Crypto

external interface Crypto {
    val subtle: SubtleCrypto
}

external interface SubtleCrypto {
    fun deriveBits(algorithm: Json, baseKey: CryptoKey, length: Number): Promise<ArrayBuffer>
    fun importKey(
        format: String,
        keyData: ArrayBuffer,
        algorithm: String,
        extractable: Boolean,
        keyUsages: Array<String>
    ): Promise<CryptoKey>

    fun importKey(
        format: String,
        keyData: ArrayBuffer,
        algorithm: Json,
        extractable: Boolean,
        keyUsages: Array<String>
    ): Promise<CryptoKey>

    fun encrypt(algorithm: Json, key: CryptoKey, data: ArrayBuffer): Promise<ArrayBuffer>
    fun decrypt(algorithm: Json, key: CryptoKey, data: ArrayBuffer): Promise<ArrayBuffer>
}

external interface CryptoKey {
    val algorithm: String
    val extractable: Boolean
    val type: String
    val usages: Array<String>
}