package net.folivo.trixnity.olm

expect class OlmPkDecryption : WantsToBeFree {
    internal val ptr: OlmPkDecryptionPointer

    companion object {
        suspend fun create(privateKey: String? = null): OlmPkDecryption
        suspend fun unpickle(key: String, pickle: String): OlmPkDecryption
    }

    val publicKey: String
    val privateKey: String

    override fun free()
    fun pickle(key: String): String

    fun decrypt(message: OlmPkMessage): String
}