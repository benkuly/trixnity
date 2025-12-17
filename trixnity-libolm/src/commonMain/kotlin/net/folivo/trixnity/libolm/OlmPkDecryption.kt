package net.folivo.trixnity.libolm

expect class OlmPkDecryption : WantsToBeFree {
    internal val ptr: OlmPkDecryptionPointer

    companion object {
        fun create(privateKey: String? = null): OlmPkDecryption
        fun unpickle(key: String?, pickle: String): OlmPkDecryption
    }

    val publicKey: String
    val privateKey: String

    override fun free()
    fun pickle(key: String?): String

    fun decrypt(message: OlmPkMessage): String
}