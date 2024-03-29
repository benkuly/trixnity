package net.folivo.trixnity.olm

expect class OlmSAS : WantsToBeFree {
    internal val ptr: OlmSASPointer

    companion object {
        suspend fun create(): OlmSAS
    }

    val publicKey: String

    override fun free()

    fun setTheirPublicKey(theirPublicKey: String)
    fun generateShortCode(info: String, numberOfBytes: Int): ByteArray
    fun calculateMac(input: String, info: String): String
    fun calculateMacFixedBase64(input: String, info: String): String
}