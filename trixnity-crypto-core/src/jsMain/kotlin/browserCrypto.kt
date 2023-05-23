import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.Json
import kotlin.js.Promise

external val crypto: Crypto

external interface Crypto {
    val subtle: SubtleCrypto
    fun getRandomValues(array: Int8Array)
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
    fun sign(algorithm: String, key: CryptoKey, data: ArrayBuffer): Promise<ArrayBuffer>
    fun sign(algorithm: Json, key: CryptoKey, data: ArrayBuffer): Promise<ArrayBuffer>
    fun digest(algorithm: String, data: ArrayBuffer): Promise<ArrayBuffer>
}

external interface CryptoKey {
    val algorithm: String
    val extractable: Boolean
    val type: String
    val usages: Array<String>
}